// Test logic QualityPolicy (ban JS giu y nhu ban Java) — chay ngay tren LUNA36.
// Cach chay: node test-quality-policy.js
// Kiem tra: tran 1080p, tat 60fps, uu tien H.264 + progressive, mac dinh 480p.

const MAX_HEIGHT = 1080;
const MAX_FPS = 30;

function codecRank(codec) {
  if (!codec) return 99;
  const c = codec.toLowerCase();
  if (c.includes('avc')) return 0;
  if (c.includes('vp9')) return 1;
  if (c.includes('av01') || c.includes('av1')) return 2;
  return 3;
}

function filter(input) {
  const ok = input.filter(s => s.height <= MAX_HEIGHT && s.fps <= MAX_FPS);
  ok.sort((a, b) => {
    if (a.isProgressive !== b.isProgressive) return a.isProgressive ? -1 : 1;
    const ca = codecRank(a.codec), cb = codecRank(b.codec);
    if (ca !== cb) return ca - cb;
    return b.height - a.height;
  });
  return ok;
}

function pickDefault(filtered) {
  if (!filtered.length) return null;
  const p480 = filtered.find(s => s.height === 480);
  return p480 || filtered[filtered.length - 1];
}

function label(s) { return `${s.height}p`; }

// Du lieu gia lap 1 video YouTube thuc te tra ve
const raw = [
  { url: 'u2160p60', height: 2160, fps: 60, codec: 'vp9', isProgressive: false },
  { url: 'u1440p60', height: 1440, fps: 60, codec: 'vp9', isProgressive: false },
  { url: 'u1080p60', height: 1080, fps: 60, codec: 'avc1', isProgressive: false },
  { url: 'u1080p30', height: 1080, fps: 30, codec: 'avc1', isProgressive: false },
  { url: 'u720p60', height: 720, fps: 60, codec: 'avc1', isProgressive: true },
  { url: 'u720p30', height: 720, fps: 30, codec: 'avc1', isProgressive: true },
  { url: 'u480p30', height: 480, fps: 30, codec: 'avc1', isProgressive: true },
  { url: 'u360p30', height: 360, fps: 30, codec: 'avc1', isProgressive: true },
];

const ok = filter(raw);
console.log(' loc con:', ok.map(s => `${label(s)}/${s.fps}fps/${s.codec}${s.isProgressive ? '/prog' : ''}`).join(' | '));

let fail = 0;
function check(name, cond) {
  console.log((cond ? 'PASS' : 'FAIL') + ' - ' + name);
  if (!cond) fail++;
}

check('loai 2160p + 1440p (qua tran 1080p)', !ok.some(s => s.height > 1080));
check('tat 60fps (khong con fps>30)', !ok.some(s => s.fps > 30));
check('con 1080p30 de xem toi da', ok.some(s => s.height === 1080 && s.fps === 30));
check('mac dinh 480p cho may phu', pickDefault(ok)?.height === 480);
check('uu tien progressive + H.264 len dau', ok[0].isProgressive === true);

// case xau nhat: video chi co 4K/60fps -> tra rong, app bao Toast
const onlyBad = filter([
  { url: 'x', height: 2160, fps: 60, codec: 'vp9', isProgressive: false },
]);
check('video chi co 4K/60fps -> rong (app se bao khong phu hop)', onlyBad.length === 0);

if (fail) { console.error(`\n${fail} check FAIL`); process.exit(1); }
console.log('\nTat ca check pass — logic 1080p/30fps OK, build APK duoc.');
