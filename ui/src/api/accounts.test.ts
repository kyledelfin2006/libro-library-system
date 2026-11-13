import { accountQueryString, isAllowedAccountSort } from './accounts';

describe('account query string', () => {
  it('drops empty filters and trims the search', () => {
    expect(accountQueryString({ page: 0, size: 12, q: '  ' })).toBe('page=0&size=12');
    expect(accountQueryString({ page: 2, size: 24, role: 'STUDENT', q: ' alex ' })).toBe('page=2&size=24&role=STUDENT&q=alex');
  });

  it('allow-lists sort fields and directions', () => {
    expect(isAllowedAccountSort('lastName,desc')).toBe(true);
    expect(isAllowedAccountSort('passwordHash,asc')).toBe(false);
    expect(isAllowedAccountSort('email,sideways')).toBe(false);
    expect(accountQueryString({ page: 0, size: 12, sort: 'passwordHash,asc' })).not.toContain('sort');
    expect(accountQueryString({ page: 0, size: 12, sort: 'email,asc' })).toContain('sort=email%2Casc');
  });

  it('clamps size to the API maximum', () => {
    expect(accountQueryString({ page: 0, size: 5000 })).toContain('size=100');
  });
});
