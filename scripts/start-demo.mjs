#!/usr/bin/env node

import { spawn, execSync } from "node:child_process";
import http from "node:http";
import path from "node:path";
import fs from "node:fs";
import { fileURLToPath } from "node:url";

// Directorio raíz del repositorio (un nivel arriba de scripts/)
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, "..");

// Utilidades para consola
const colors = {
  reset: "\x1b[0m",
  green: "\x1b[32m",
  red: "\x1b[31m",
  yellow: "\x1b[33m",
  cyan: "\x1b[36m",
  bold: "\x1b[1m",
};

function logInfo(msg) {
  console.log(`${colors.cyan}[INFO]${colors.reset} ${msg}`);
}

function logSuccess(msg) {
  console.log(`${colors.green}✔ ${msg}${colors.reset}`);
}

function logError(msg) {
  console.error(`\n${colors.red}${colors.bold}✖ Error:${colors.reset} ${msg}\n`);
}

function commandExists(cmd) {
  try {
    const checkCmd = process.platform === "win32" ? `where ${cmd}` : `which ${cmd}`;
    execSync(checkCmd, { stdio: "ignore" });
    return true;
  } catch {
    return false;
  }
}

// Ejecuta un comando heredando la salida estándar
function runCommand(cmd, args, cwd = ROOT_DIR) {
  return new Promise((resolve, reject) => {
    const proc = spawn(cmd, args, {
      cwd,
      stdio: "inherit",
      shell: true,
    });

    proc.on("close", (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`El comando "${cmd} ${args.join(" ")}" finalizó con código de error ${code}.`));
      }
    });

    proc.on("error", (err) => reject(err));
  });
}

// Sondeo del endpoint de salud del API
function waitForApiHealth(url = "http://localhost:8080/actuator/health", maxRetries = 30, intervalMs = 2000) {
  return new Promise((resolve, reject) => {
    let attempts = 0;

    const check = () => {
      attempts++;
      http.get(url, (res) => {
        if (res.statusCode === 200) {
          return resolve();
        }
        retry();
      }).on("error", () => {
        retry();
      });
    };

    const retry = () => {
      if (attempts >= maxRetries) {
        reject(new Error(`La API no respondió saludablemente en ${url} tras ${maxRetries * (intervalMs / 1000)} segundos.`));
      } else {
        setTimeout(check, intervalMs);
      }
    };

    check();
  });
}

async function main() {
  console.log(`\n${colors.bold}=== Despliegue de Demostración y Túnel Cloudflare ===${colors.reset}\n`);

  // -------------------------------------------------------------
  // PASO 1: Validar compilación del backend con Maven
  // -------------------------------------------------------------
  logInfo("Paso 1/3: Validando compilación del backend...");
  
  const mvnwPath = path.join(ROOT_DIR, process.platform === "win32" ? "mvnw.cmd" : "mvnw");
  if (!fs.existsSync(mvnwPath)) {
    logError(`No se encontró el wrapper de Maven en "${mvnwPath}".`);
    process.exit(1);
  }

  if (process.platform !== "win32") {
    try {
      fs.chmodSync(mvnwPath, 0o755);
    } catch {
      // Ignorar si no se tienen permisos para chmod
    }
  }

  try {
    // test-compile compila código principal y clases de prueba sin ejecutar la suite
    await runCommand(mvnwPath, ["test-compile", "-B"], ROOT_DIR);
    logSuccess("El backend compiló correctamente.\n");
  } catch (err) {
    logError("La compilación del backend falló. Revisa los errores del compilador arriba antes de continuar.");
    process.exit(1);
  }

  // -------------------------------------------------------------
  // PASO 2: Levantar stack con Docker Compose
  // -------------------------------------------------------------
  logInfo("Paso 2/3: Levantando PostgreSQL y API con Docker Compose...");

  if (!commandExists("docker")) {
    logError("'docker' no está instalado o no se encuentra en el PATH.");
    process.exit(1);
  }

  // Verificar que el daemon de Docker esté respondiendo
  try {
    execSync("docker info", { stdio: "ignore" });
  } catch {
    logError("El servicio de Docker no está en ejecución. Por favor inicia Docker Desktop o el daemon de Docker.");
    process.exit(1);
  }

  const composeFile = path.join(ROOT_DIR, "compose.demo.yml");
  if (!fs.existsSync(composeFile)) {
    logError(`No se encontró el archivo "${composeFile}".`);
    process.exit(1);
  }

  try {
    await runCommand("docker", ["compose", "-f", "compose.demo.yml", "up", "-d", "--build"], ROOT_DIR);
    logInfo("Esperando a que el API termine de arrancar y esté saludable (http://localhost:8080/actuator/health)...");
    await waitForApiHealth();
    logSuccess("API y base de datos listas y respondiendo en el puerto 8080.\n");
  } catch (err) {
    logError(
      `Falló el arranque de Docker Compose o el API no estuvo lista a tiempo.\n` +
      `   Detalle: ${err.message}\n` +
      `   Para ver los logs de error ejecuta: docker compose -f compose.demo.yml logs api`
    );
    process.exit(1);
  }

  // -------------------------------------------------------------
  // PASO 3: Levantar Cloudflare Tunnel (trycloudflare)
  // -------------------------------------------------------------
  logInfo("Paso 3/3: Levantando Cloudflare Tunnel...");

  if (!commandExists("cloudflared")) {
    logError(
      "'cloudflared' no está instalado en este sistema.\n" +
      "   Para instalarlo:\n" +
      "     - Arch Linux: sudo pacman -S cloudflared\n" +
      "     - Ubuntu/Debian: sudo apt-get install cloudflared\n" +
      "     - macOS: brew install cloudflared\n" +
      "     - Descarga directa: https://github.com/cloudflare/cloudflared/releases"
    );
    process.exit(1);
  }

  const tunnelProc = spawn("cloudflared", ["tunnel", "--url", "http://localhost:8080"], {
    stdio: ["ignore", "pipe", "pipe"],
  });

  let tunnelUrlFound = false;

  const handleTunnelOutput = (data) => {
    const text = data.toString();
    const match = text.match(/https:\/\/[a-zA-Z0-9-]+\.trycloudflare\.com/);
    if (match && !tunnelUrlFound) {
      tunnelUrlFound = true;
      const url = match[0];
      console.log(`\n${colors.bold}${colors.green}======================================================================${colors.reset}`);
      console.log(`${colors.bold}🌐 ¡Túnel de Cloudflare activo con éxito!${colors.reset}`);
      console.log(`🔗 Enlace público: ${colors.cyan}${colors.bold}${url}${colors.reset}`);
      console.log(`${colors.yellow}Presiona Ctrl+C para detener el túnel.${colors.reset}`);
      console.log(`${colors.bold}${colors.green}======================================================================${colors.reset}\n`);
    }
  };

  tunnelProc.stdout.on("data", handleTunnelOutput);
  tunnelProc.stderr.on("data", handleTunnelOutput);

  tunnelProc.on("close", (code) => {
    if (code !== 0 && !tunnelUrlFound) {
      logError(`cloudflared se cerró inesperadamente con código de salida ${code}.`);
    }
    process.exit(code || 0);
  });

  // Limpieza al pulsar Ctrl+C
  process.on("SIGINT", () => {
    console.log(`\n${colors.yellow}Cerrando túnel de Cloudflare...${colors.reset}`);
    tunnelProc.kill("SIGINT");
    process.exit(0);
  });

  process.on("SIGTERM", () => {
    tunnelProc.kill("SIGTERM");
    process.exit(0);
  });
}

main().catch((err) => {
  logError(`Ocurrió un error inesperado: ${err.message}`);
  process.exit(1);
});
