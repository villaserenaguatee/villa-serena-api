import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { compare, readManifest, verifyCheckout } from './api-contract.mjs';

function fixture(t, tool) {
  const folder = mkdtempSync(join(tmpdir(), 'vs-contract-'));
  t.after(() => rmSync(folder, { recursive: true, force: true }));
  const docs = join(folder, 'docs con espacios');
  mkdirSync(join(docs, 'scripts'), { recursive: true });
  writeFileSync(join(docs, 'openapi.yaml'), 'openapi: 3.1.0\npaths: {}\n');
  writeFileSync(join(docs, 'package.json'), '{}');
  writeFileSync(join(docs, 'pnpm-lock.yaml'), 'lockfileVersion: 9');
  writeFileSync(join(docs, '.gitignore'), 'node_modules/\n');
  writeFileSync(join(docs, 'scripts/contrato-api.mjs'), tool ?? `
    import {readFileSync} from 'node:fs';
    const input=JSON.parse(readFileSync(process.argv[process.argv.indexOf('--schema')+1]));
    const differences=input.fixtureDifferences;
    console.log(JSON.stringify(differences));
    console.log('Comparación de rutas y métodos únicamente; requiere pruebas adicionales de DTO y seguridad.');
    process.exitCode=differences.missing.length||differences.extra.length?1:0;
  `);
  const git = (...args) => execFileSync('git', ['-C', docs, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
  git('init');
  git('add', '.');
  git('-c', 'user.name=Prueba', '-c', 'user.email=prueba@example.test', 'commit', '-m', 'fixture');
  return { folder, docs, git, manifest: { repository: 'villaserenaguatee/villa-serena-docs', revision: git('rev-parse', 'HEAD') } };
}

test('el manifiesto exige repositorio canónico y SHA completo, sin ramas ni marcadores', t => {
  const { folder, manifest } = fixture(t);
  const file = join(folder, 'api-contract.json');
  for (const value of [{ ...manifest, revision: 'main' }, { ...manifest, revision: 'abc1234' },
    { ...manifest, repository: 'otra/copia' }, { ...manifest, revision: '<SHA>' }]) {
    writeFileSync(file, JSON.stringify(value));
    assert.throws(() => readManifest(file), /repositorio de docs y un SHA/);
  }
  writeFileSync(file, JSON.stringify(manifest));
  assert.deepEqual(readManifest(file), manifest);
});

test('acepta checkout limpio y rutas con espacios; rechaza otro commit o una subcarpeta', t => {
  const { docs, manifest } = fixture(t);
  assert.equal(verifyCheckout(docs, manifest), docs);
  assert.throws(() => verifyCheckout(docs, { ...manifest, revision: '0'.repeat(40) }), /revisión de docs/);
  assert.throws(() => verifyCheckout(join(docs, 'scripts'), manifest), /raíz del checkout/);
  assert.throws(() => verifyCheckout(undefined, manifest), /--docs/);
});

for (const name of ['openapi.yaml', 'scripts/contrato-api.mjs', 'package.json', 'pnpm-lock.yaml', 'nota-nueva.md']) {
  test(`rechaza cambios locales en ${name}`, t => {
    const { docs, manifest } = fixture(t);
    writeFileSync(join(docs, name), 'modificado');
    assert.throws(() => verifyCheckout(docs, manifest), /cambios locales/);
  });
}

test('dependencias ignoradas no convierten el checkout instalado en modificado', t => {
  const { docs, manifest } = fixture(t);
  mkdirSync(join(docs, 'node_modules'));
  writeFileSync(join(docs, 'node_modules', 'ejemplo'), 'instalado');
  assert.equal(verifyCheckout(docs, manifest), docs);
});

test('conserva todas las diferencias en el artefacto, incluso en modo diagnóstico', t => {
  const { folder, docs, manifest } = fixture(t);
  const differences = { missing: ['POST /acordada'], extra: ['GET /implementada'] };
  const schema = join(folder, 'spring.json');
  const output = join(folder, 'target', 'report.json');
  writeFileSync(schema, JSON.stringify({ fixtureDifferences: differences }));
  const report = compare(verifyCheckout(docs, manifest), manifest, schema, { output, reportOnly: true });
  assert.equal(report.matches, false);
  assert.equal(report.mode, 'diagnostic');
  assert.deepEqual(report.missing, differences.missing);
  assert.deepEqual(report.extra, differences.extra);
  assert.equal(report.contractSha256.length, 64);
  assert.deepEqual(JSON.parse(readFileSync(output)), report);
  assert.equal(compare(docs, manifest, schema, { output }).mode, 'strict');
});

test('reconoce operaciones coincidentes sin declarar conformidad de DTO ni seguridad', t => {
  const { folder, docs, manifest } = fixture(t);
  const schema = join(folder, 'spring.json');
  writeFileSync(schema, JSON.stringify({ fixtureDifferences: { missing: [], extra: [] } }));
  const report = compare(docs, manifest, schema, { output: join(folder, 'report.json') });
  assert.equal(report.matches, true);
  assert.equal(report.scope, 'HTTP paths and methods only');
});

test('sin exportación no compara ni inventa un contrato de respaldo', t => {
  const { folder, docs, manifest } = fixture(t);
  assert.throws(() => compare(docs, manifest, join(folder, 'ausente.json')), /exportación de Spring/);
});

test('los fallos de herramientas siguen siendo errores en modo diagnóstico', t => {
  const { folder, docs, manifest } = fixture(t, 'console.error("dependencia ausente"); process.exitCode=1;');
  const schema = join(folder, 'spring.json');
  writeFileSync(schema, '{}');
  assert.throws(() => compare(docs, manifest, schema, { reportOnly: true }), /dependencia ausente/);
});

test('rechaza informes o códigos de salida inconsistentes', t => {
  const { folder, docs, manifest } = fixture(t, 'console.log(JSON.stringify({missing:[],extra:[]})); process.exitCode=1;');
  const schema = join(folder, 'spring.json');
  writeFileSync(schema, '{}');
  assert.throws(() => compare(docs, manifest, schema, { reportOnly: true }), /código de salida/);
});
