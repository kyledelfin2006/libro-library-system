// Derives sized brand assets from ../assets/LIBRO_LOGO.png (plan section 5.3). The source is never modified and
// the logo is only resized, cropped, or padded: never recolored, stretched, or redrawn.
import sharp from 'sharp';
import pngToIco from 'png-to-ico';
import { mkdir, writeFile } from 'node:fs/promises';

const SRC = new URL('../../assets/LIBRO_LOGO.png', import.meta.url);
const OUT = new URL('../public/brand/', import.meta.url);
await mkdir(OUT, { recursive: true });

const logo = () => sharp(SRC.pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const out = (name) => new URL(name, OUT).pathname.replace(/^\/([A-Za-z]:)/, '$1');

// Lockup: header (48/96/192 wide), sign-in panel (256), document icon (512).
for (const w of [48, 96, 192, 256, 512]) {
  await logo().resize({ width: w }).png({ compressionLevel: 9 }).toFile(out(`libro-${w}.png`));
}

// Favicons use the book glyph only (the wordmark is unreadable at 16 px), centred on a transparent square.
const glyph = await logo().extract({ left: 95, top: 85, width: 1060, height: 640 }).toBuffer();
const square = await sharp(glyph).resize({ width: 1060, height: 1060, fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toBuffer();
const sizes = { 'favicon-16.png': 16, 'favicon-32.png': 32, 'apple-touch-icon.png': 180, 'icon-512.png': 512 };
for (const [name, size] of Object.entries(sizes)) {
  await sharp(square).resize(size, size).png({ compressionLevel: 9 }).toFile(out(name));
}
await writeFile(out('favicon.ico'), await pngToIco([out('favicon-16.png'), out('favicon-32.png')]));

// 1200x630 social preview: the lockup centred on the dark brand background.
const lockup = await logo().resize({ height: 520 }).png().toBuffer();
await sharp({ create: { width: 1200, height: 630, channels: 4, background: '#0C1611' } })
  .composite([{ input: lockup, gravity: 'center' }])
  .png({ compressionLevel: 9 })
  .toFile(out('social.png'));
console.log('brand assets written');
