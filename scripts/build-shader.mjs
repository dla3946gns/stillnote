import { build } from 'esbuild';
import { readFile, writeFile } from 'node:fs/promises';

await build({
  entryPoints: ['src/sidebar-shader.js'],
  outfile: 'dist/assets/sidebar-shader.js',
  bundle: true,
  minify: true,
  format: 'esm',
  target: 'es2022',
  legalComments: 'external'
});

const packages = ['shaders', 'typegpu', 'tinyest', 'tsover-runtime', 'typed-binary'];
const licenses = await Promise.all(packages.map(async name => {
  const license = await readFile(`node_modules/${name}/LICENSE`, 'utf8');
  return `${name}\n${license}`;
}));
await writeFile('dist/assets/shader-LICENSES.txt', licenses.join('\n\n'));
