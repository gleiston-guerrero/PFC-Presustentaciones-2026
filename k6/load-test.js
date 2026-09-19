import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 20 }, // Ramp-up to 20 users
        { duration: '1m', target: 50 },  // Stay at 50 users (peak)
        { duration: '30s', target: 0 },  // Ramp-down to 0
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% of requests under 500ms
        http_req_failed: ['rate<0.01'],   // Less than 1% errors
    },
};

// Desde que se implemento el versionado dinamico (/api/v1), el backend exige
// la ruta versionada; /api/auth/login sin version ahora devuelve 403.
const BASE_URL = `${__ENV.BASE_URL || 'http://localhost:8080/api/v1'}`;

// setup() corre UNA sola vez (no por VU/iteracion) y hace un unico login real.
// Motivo del cambio de metodologia respecto a runs anteriores: desde que se
// agrego rate limiting en /auth/login (6 intentos/60s -> 429), hacer login en
// cada iteracion con 50 VUs concurrentes satura el limite de inmediato y el
// 99% de las peticiones terminan bloqueadas por diseno (no es un fallo real
// de capacidad, es el rate limiter funcionando). El login se prueba aparte;
// esta prueba de carga mide el flujo real de un usuario ya autenticado.
// Las credenciales se leen del entorno, no se escriben aqui (senalado en la
// revision del 18-sep). La cuenta es la que siembra DemoDataSeeder, que existe
// SOLO bajo el perfil `dev`: un despliegue sin ese perfil nunca la crea. Aun
// asi, una cadena con pinta de contrasena en un repositorio publico se lee como
// una contrasena, y el lector no tiene forma de saber que no lo es.
//
//   k6 run -e K6_USER=admin@uteq.edu.ec -e K6_PASS=<la del seeder> load-test.js
//
// De paso: el valor que estaba escrito ('admin123') ya no era el del seeder, asi
// que este setup habria fallado. Al pedirlo por entorno el fallo es explicito en
// vez de silencioso.
const USER = __ENV.K6_USER || 'admin@uteq.edu.ec';
const PASS = __ENV.K6_PASS;

export function setup() {
    if (!PASS) {
        throw new Error(
            'Falta K6_PASS. Esta prueba necesita en el entorno la contrasena del ' +
            'usuario de demostracion (perfil dev, ver DemoDataSeeder), no escrita ' +
            'en este archivo. Ejemplo: k6 run -e K6_PASS=... load-test.js');
    }
    const payload = JSON.stringify({ email: USER, password: PASS });
    const params = { headers: { 'Content-Type': 'application/json' } };
    const res = http.post(`${BASE_URL}/auth/login`, payload, params);
    if (res.status !== 200) {
        throw new Error(`Setup login failed with status ${res.status}: ${res.body}`);
    }
    const token = JSON.parse(res.body).data.auth.token;
    return { token };
}

export default function (data) {
    const authHeaders = { headers: { Authorization: `Bearer ${data.token}` } };

    // Peticion autenticada representativa del uso real (catalogo de modalidades).
    // Nota: el script original apuntaba a /catalogos/carreras, que nunca existio
    // en el backend (CatalogoController solo expone /modalidades, /convocatorias
    // y /convocatoria-activa) -- siempre devolvia 403 antes de llegar al enrutador,
    // por eso el error nunca se noto. Se corrige a un endpoint real.
    const catalogRes = http.get(`${BASE_URL}/catalogos/modalidades`, authHeaders);
    check(catalogRes, {
        'catalogos status is 200': (r) => r.status === 200,
    });

    // Endpoint con cache Redis activa (Requisito E2): universidades de Ecuador
    const univRes = http.get(`${BASE_URL}/universidades`, authHeaders);
    check(univRes, {
        'universidades status is 200': (r) => r.status === 200,
    });

    sleep(1);
}
