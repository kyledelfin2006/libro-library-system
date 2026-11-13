import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { createRequire } from 'node:module';

/**
 * The one end-to-end journey: a student registers, signs in, searches, borrows and returns a book, borrows a second
 * one, and library staff resolve that loan as lost. It runs against a real API and database through the UI.
 *
 * It creates its own data (a uniquely named pair of books and a new student), so it can run repeatedly, but it does
 * write to that database: point it at a disposable one. The staff account comes from E2E_STAFF_ID / E2E_STAFF_PASSWORD.
 */
const staffId = process.env.E2E_STAFF_ID;
const staffPassword = process.env.E2E_STAFF_PASSWORD;
const stamp = Date.now().toString(36);
const digits = (n: number) => String(Math.floor(Math.random() * 10 ** n)).padStart(n, '0');
// 8xxx-xxxx is outside the demo seed ranges. A random ID keeps repeat runs from colliding.
const studentId = `8${digits(3)}-${digits(4)}`;
const studentPassword = 'E2e-Journey-1!x';
const titleReturned = `E2E Returned ${stamp}`;
const titleLost = `E2E Lost ${stamp}`;

const axeSource = createRequire(import.meta.url).resolve('axe-core');

/** Runs axe on the current page in the given color scheme and fails on serious or critical violations. */
async function expectAccessible(page: Page, scheme: 'light' | 'dark') {
  await page.emulateMedia({ colorScheme: scheme });
  await page.addScriptTag({ path: axeSource });
  const violations = await page.evaluate(async () => {
    // axe is injected into the page by the script tag above.
    const axe = (window as unknown as { axe: { run: (c: Document, o: object) => Promise<{ violations: { id: string; impact: string; nodes: unknown[] }[] }> } }).axe;
    const result = await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa'] });
    return result.violations
      .filter((v) => v.impact === 'serious' || v.impact === 'critical')
      .map((v) => `${v.id} (${v.nodes.length})`);
  });
  expect(violations, `${scheme} theme on ${page.url()}`).toEqual([]);
}

async function addBook(api: APIRequestContext, token: string, title: string) {
  const response = await api.post('/app/books/add', {
    headers: { Authorization: `Bearer ${token}` },
    data: { title, author: 'E2E Author', genre: 'E2E', description: 'Created by the end-to-end journey.', replacementCost: 12.5 },
  });
  expect(response.status(), `creating ${title}`).toBe(201);
}

async function signIn(page: Page, universityId: string, password: string) {
  await page.goto('/signin');
  await page.getByLabel('University ID').fill(universityId);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

/** Searches the catalog for an exact title and opens its first result. */
async function openFromCatalog(page: Page, title: string) {
  await page.getByRole('link', { name: 'Catalog' }).first().click();
  await page.getByLabel('Search titles').fill(title);
  await page.getByRole('link', { name: new RegExp(title) }).first().click();
  await expect(page.getByRole('heading', { name: title })).toBeVisible();
}

test.describe.serial('student and staff journey', () => {
  test.skip(!staffId || !staffPassword, 'Set E2E_STAFF_ID and E2E_STAFF_PASSWORD for a staff account on the test API.');

  test.beforeAll(async ({ playwright, baseURL }) => {
    const api = await playwright.request.newContext({ baseURL: baseURL! });
    const login = await api.post('/app/auth/login', { data: { universityId: staffId, password: staffPassword } });
    expect(login.ok(), 'staff login').toBeTruthy();
    const token = (await login.json()).data.accessToken as string;
    await addBook(api, token, titleReturned);
    await addBook(api, token, titleLost);
    await api.dispose();
  });

  test('registers a student account', async ({ page }) => {
    await page.goto('/register');
    await expectAccessible(page, 'light');
    await expectAccessible(page, 'dark');
    await page.getByLabel('University ID').fill(studentId);
    await page.getByLabel('First name').fill('Eve');
    await page.getByLabel('Last name').fill('Journey');
    await page.getByLabel('Email').fill(`journey.${stamp}.${studentId}@example.test`);
    await page.getByLabel('Course').selectOption('IS');
    await page.getByLabel('Password', { exact: true }).fill(studentPassword);
    await page.getByRole('button', { name: 'Create account' }).click();
    // Registration sends the new student to sign in.
    await expect(page.getByRole('heading', { name: /Sign in/ })).toBeVisible();
  });

  test('signs in, searches, borrows, then returns a book', async ({ page }) => {
    await signIn(page, studentId, studentPassword);
    await expect(page.getByRole('heading', { name: 'Catalog' })).toBeVisible();
    await expectAccessible(page, 'light');
    await expectAccessible(page, 'dark');

    await openFromCatalog(page, titleReturned);
    await page.getByRole('button', { name: 'Borrow this copy' }).click();
    await expect(page.getByText(/^Borrowed\. Due /)).toBeVisible();

    await page.getByRole('link', { name: 'My Loans' }).click();
    await expect(page.getByRole('heading', { name: 'My loans' })).toBeVisible();
    await expectAccessible(page, 'light');
    await expectAccessible(page, 'dark');
    await page.getByRole('button', { name: new RegExp(`Return ${titleReturned}`) }).click();
    await page.getByRole('button', { name: 'Return book' }).click();
    await expect(page.getByText(new RegExp(`Returned ${titleReturned}`))).toBeVisible();
  });

  test('borrows a second book that staff then mark as lost', async ({ page }) => {
    await signIn(page, studentId, studentPassword);
    await openFromCatalog(page, titleLost);
    await page.getByRole('button', { name: 'Borrow this copy' }).click();
    await expect(page.getByText(/^Borrowed\. Due /)).toBeVisible();
    await page.getByRole('button', { name: 'Sign out' }).click();

    // Staff resolve the loan from the loans page, filtered to this borrower.
    await signIn(page, staffId!, staffPassword!);
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Loans', exact: true }).click();
    await page.getByLabel('Borrower ID').fill(studentId);
    await page.getByRole('button', { name: 'Search' }).click();
    const row = page.getByRole('row', { name: new RegExp(titleLost) });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: /Details/ }).click();
    await page.getByRole('button', { name: 'Mark as lost' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Mark as lost' }).click();
    await expect(page.getByText(`Marked ${titleLost} as lost.`)).toBeVisible();
    await expectAccessible(page, 'light');
    await expectAccessible(page, 'dark');
  });
  test('collapses the navigation behind a Menu button on a phone', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 800 });
    await signIn(page, studentId, studentPassword);
    const nav = page.getByRole('navigation', { name: 'Primary' });
    const menu = page.getByRole('button', { name: 'Menu' });
    await expect(menu).toBeVisible();
    await expect(nav).toBeHidden();
    await menu.click();
    await expect(nav).toBeVisible();
    await expectAccessible(page, 'light');
    await expectAccessible(page, 'dark');
    await nav.getByRole('link', { name: 'My Loans' }).click();
    await expect(nav).toBeHidden();
    // No horizontal scrolling at phone width.
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
});
