const [nipd, code] = process.argv.slice(2);
const { generateKeyPairSync, sign } = require('node:crypto');
const B = 'http://127.0.0.1:8181';
const post = async (p, b, t) => {
  const r = await fetch(B + p, { method: 'POST', headers: { 'content-type': 'application/json', ...(t ? { authorization: 'Bearer ' + t } : {}) }, body: JSON.stringify(b) });
  return { status: r.status, body: await r.json() };
};
const get = async (p, t) => {
  const r = await fetch(B + p, { headers: t ? { authorization: 'Bearer ' + t } : {} });
  return { status: r.status, body: await r.json() };
};
(async () => {
  const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const pub = publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
  const act = await post('/auth/activate', { nipd, code, pin: '123456', device: { publicKey: pub, label: 'uji-smoke', model: 'smoke' } });
  console.log('1 aktivasi      :', act.status, act.status === 200 ? 'deviceLimit=' + act.body.device.deviceLimit : act.body.code);
  if (act.status !== 200) return;
  const dev = act.body.device.id;
  const ch = await post('/auth/challenge', { nipd, deviceId: dev });
  console.log('2 challenge     :', ch.status, ch.body.algorithm || ch.body.code);
  const sig = sign('sha256', Buffer.from(ch.body.nonce, 'utf8'), privateKey).toString('base64');
  const salah = await post('/auth/login', { nipd, deviceId: dev, signature: sig, pin: '999999' });
  console.log('3 PIN salah     :', salah.status, salah.body.code);
  const li = await post('/auth/login', { nipd, deviceId: dev, signature: sig, pin: '123456' });
  console.log('4 login + tanda tangan:', li.status, li.body.accessToken ? 'token ok, refresh ' + (li.body.refreshToken ? 'ada' : 'tidak') : li.body.code);
  if (!li.body.accessToken) return;
  const g = await get('/super/villages', li.body.accessToken);
  console.log('5 guard peran   :', g.status, g.body.code || 'ok');
  const r = await post('/auth/refresh', { refreshToken: li.body.refreshToken });
  console.log('6 refresh token :', r.status, r.body.accessToken ? 'rotasi ok' : r.body.code);
  const bogus = await post('/auth/challenge', { nipd, deviceId: '00000000-0000-0000-0000-000000000000' });
  console.log('7 device asing  :', bogus.status, bogus.body.code);
})();