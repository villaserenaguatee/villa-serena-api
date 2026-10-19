#!/usr/bin/env node
import { execFileSync, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';

const root = fileURLToPath(new URL('../', import.meta.url));

export function readManifest(path = resolve(root, 'api-contract.json')) {
  const manifest = JSON.parse(readFileSync(path, 'utf8'));
  if (manifest.repository !== 'villaserenaguatee/villa-serena-docs' ||
      !/^[a-f0-9]{40}$/.test(manifest.revision ?? '')) {
    throw new Error('api-contract.json requiere el repositorio de docs y un SHA completo aprobado.');
  }
  return { repository: manifest.repository, revision: manifest.revision };
}

export function verifyCheckout(directory, manifest) {
  if (!directory) throw new Error('Indica --docs RUTA o VILLA_SERENA_DOCS_DIR con el checkout fijado de docs.');
  const docs = resolve(directory);
  const git = (...args) => execFileSync('git', ['-C', docs, ...args], { encoding: 'utf8' }).trim();
  if (git('rev-parse', '--show-toplevel') !== docs.replaceAll('\\', '/')) {
    // Windows puede devolver separadores distintos; resolve compara la misma raíz.
    if (resolve(git('rev-parse', '--show-toplevel')) !== docs) throw new Error('La ruta debe ser la raíz del checkout de docs.');
  }
  if (git('rev-parse', 'HEAD') !== manifest.revision) throw new Error('La revisión de docs no coincide con api-contract.json.');
  if (git('status', '--porcelain', '--untracked-files=all')) throw new Error('El checkout de docs tiene cambios locales; usa un checkout limpio de la revisión fijada.');
  for (const file of ['openapi.yaml', 'scripts/contrato-api.mjs', 'package.json', 'pnpm-lock.yaml']) {
    if (!existsSync(resolve(docs, file))) throw new Error(`Falta ${file} en el checkout de docs.`);
  }
  return docs;
}

export function compare(docs, manifest, schema, { reportOnly = false, output = resolve(root, 'target/contract-comparison.json') } = {}) {
  if (!existsSync(schema)) throw new Error('Falta la exportación de Spring; ejecuta ./mvnw -B verify primero.');
  const result = spawnSync(process.execPath, [resolve(docs, 'scripts/contrato-api.mjs'), 'check-api',
    '--schema', resolve(schema), '--revision', manifest.revision], { cwd: docs, encoding: 'utf8' });
  if (result.error) throw result.error;
  // Un fallo de herramientas o dependencias no se convierte en un diagnóstico válido.
  let differences;
  try {
    const text = result.stdout ?? '';
    differences = JSON.parse(text.slice(text.indexOf('{'), text.lastIndexOf('}') + 1));
  } catch {
    throw new Error(`No se pudo comparar el contrato: ${result.stderr || result.stdout || `salida ${result.status}`}`);
  }
  if (!Array.isArray(differences.missing) || !Array.isArray(differences.extra) ||
      ![...differences.missing, ...differences.extra].every(value => typeof value === 'string') ||
      ![0, 1].includes(result.status)) throw new Error('La herramienta de docs devolvió un resultado inválido.');
  const matches = differences.missing.length === 0 && differences.extra.length === 0;
  if (result.status !== (matches ? 0 : 1)) throw new Error('El resultado y el código de salida de docs no coinciden.');
  const report = { ...manifest, contractSha256: createHash('sha256').update(readFileSync(resolve(docs, 'openapi.yaml'))).digest('hex'),
    mode: reportOnly ? 'diagnostic' : 'strict', scope: 'HTTP paths and methods only', matches, ...differences };
  mkdirSync(dirname(output), { recursive: true });
  writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
  return report;
}

export function main(args = process.argv.slice(2)) {
  const [command, ...rest] = args;
  const { values, positionals } = parseArgs({ args: rest, allowPositionals: true, options: {
    docs: { type: 'string' }, schema: { type: 'string' }, 'report-only': { type: 'boolean', default: false },
  } });
  if (!['metadata', 'verify', 'check'].includes(command) || positionals.length ||
      (command !== 'check' && (values.schema || values['report-only'])) || (command === 'metadata' && values.docs)) {
    throw new Error('Uso: api-contract.mjs metadata | verify --docs RUTA | check --docs RUTA [--schema RUTA] [--report-only]');
  }
  const manifest = readManifest();
  if (command === 'metadata') {
    console.log(JSON.stringify(manifest, null, 2));
    if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT,
      `repository=${manifest.repository}\nrevision=${manifest.revision}\n`);
    return;
  }
  const docs = verifyCheckout(values.docs ?? process.env.VILLA_SERENA_DOCS_DIR, manifest);
  if (command === 'verify') {
    execFileSync(process.execPath, [resolve(docs, 'scripts/contrato-api.mjs'), 'info', '--revision', manifest.revision],
      { cwd: docs, stdio: 'inherit' });
    return;
  }
  const report = compare(docs, manifest, resolve(values.schema ?? resolve(root, 'target/openapi-spring.json')),
    { reportOnly: values['report-only'] });
  const summary = `Contrato ${manifest.revision}: ${report.missing.length} operaciones faltantes y ${report.extra.length} no declaradas.\n` +
    `Modo ${report.mode}. Comparación de rutas/métodos; no verifica DTO ni seguridad.\n` +
    (report.matches ? 'Las operaciones coinciden.\n' : 'CONFORMIDAD PENDIENTE: ver target/contract-comparison.json y docs/CONTRATO_API.md.\n');
  console.log(summary);
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, summary + '\n');
  if (!report.matches && !values['report-only']) process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try { main(); } catch (error) { console.error(error.message); process.exitCode = 1; }
}
