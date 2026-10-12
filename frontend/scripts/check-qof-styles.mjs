import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const loadingComponent = join(root, 'app/shared/loading/loading.component.ts');
const failures = [];
function check(directory) {
  for (const item of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, item.name);
    if (item.isDirectory()) { check(path); continue; }
    if (!/\.(css|ts|html)$/.test(item.name) || item.name.endsWith('.spec.ts')) continue;
    let source = readFileSync(path, 'utf8');
    // Only the shared loading spinner may have a circular radius.
    if (path === loadingComponent) {
      const circularSpinner = /(\.spinner\s*\{[^}]*?)border-radius:\s*50%\s*;/;
      if (!circularSpinner.test(source)) failures.push(`${path}: loading spinner must be circular`);
      source = source.replace(circularSpinner, '$1border-radius: 0;');
    }
    for (const [index, line] of source.split('\n').entries()) {
      if (/\b(?:linear|radial|conic)-gradient\s*\(/i.test(line)) failures.push(`${path}:${index + 1}: gradient`);
      if (/border(?:-(?:top|bottom)-(?:left|right))?-radius\s*:/.test(line) && !/border(?:-(?:top|bottom)-(?:left|right))?-radius\s*:\s*0\s*;/.test(line)) {
        failures.push(`${path}:${index + 1}: rounded corners`);
      }
    }
  }
}
check(root);
if (failures.length) { console.error(failures.join('\n')); process.exitCode = 1; }
else console.log('QoF style check passed: circular loading spinner, square corners elsewhere, and no CSS gradients.');
