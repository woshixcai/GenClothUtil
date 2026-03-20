/* ================================================================
   StyleMe · main.js
   功能：主题切换 / JWT 登录 / 图片上传预览 / AI 换装请求 / 结果展示
   ================================================================ */

/* ── 全局状态 ─────────────────────────────────────────────────── */
const S = {
  token:         localStorage.getItem('sm_token')       || '',
  username:      localStorage.getItem('sm_username')    || '',
  permissions:   JSON.parse(localStorage.getItem('sm_perms') || '[]'),
  theme:         localStorage.getItem('sm_theme')       || 'gentle-luxury',
  clothesFiles:  [],
  refFiles:      [],
  resultImgUrls: [],
};

/* 权限辅助 */
function hasPerm(code) { return S.permissions.includes(code); }
function isManager()   { return hasPerm('sys:manage') || hasPerm('user:shop_manage'); }

/* ── 入口 ─────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  initAuth();
  initUpload();
  initGenerate();
  initModal();
});

/* ================================================================
   主题切换
   ================================================================ */
function initTheme() {
  applyTheme(S.theme);

  document.querySelectorAll('.tdot').forEach(dot => {
    dot.addEventListener('click', () => {
      applyTheme(dot.dataset.theme);
    });
  });
}

function applyTheme(theme) {
  S.theme = theme;
  document.documentElement.dataset.theme = theme;
  localStorage.setItem('sm_theme', theme);

  document.querySelectorAll('.tdot').forEach(d => {
    d.classList.toggle('active', d.dataset.theme === theme);
  });

  // 更新移动端状态栏颜色
  const colors = {
    'gentle-luxury': '#FDFAF7',
    'sweet-fresh':   '#FFF8FC',
    'premium':       '#F8F6F3',
  };
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.content = colors[theme] || '#FDFAF7';
}

/* ================================================================
   登录 / 登出
   ================================================================ */
function initAuth() {
  document.getElementById('loginBtn').addEventListener('click', openModal);
  document.getElementById('logoutBtn').addEventListener('click', doLogout);
  renderAuthUI();
}

function renderAuthUI() {
  const loggedIn = !!S.token;
  document.getElementById('loginBtn').classList.toggle('hidden', loggedIn);
  document.getElementById('userChip').classList.toggle('hidden', !loggedIn);

  if (loggedIn) {
    document.getElementById('userName').textContent   = S.username;
    document.getElementById('userAvatar').textContent = S.username.charAt(0).toUpperCase();
  }

  // 管理入口：有管理权限才显示
  const adminNav = document.getElementById('adminNavItem');
  if (adminNav) adminNav.style.display = (loggedIn && isManager()) ? '' : 'none';

  refreshHint();
}

function doLogout() {
  S.token       = '';
  S.username    = '';
  S.permissions = [];
  localStorage.removeItem('sm_token');
  localStorage.removeItem('sm_username');
  localStorage.removeItem('sm_perms');
  renderAuthUI();
}

/* ================================================================
   登录模态框
   ================================================================ */
function initModal() {
  document.getElementById('modalX').addEventListener('click', closeModal);
  document.getElementById('modalBg').addEventListener('click', e => {
    if (e.target === e.currentTarget) closeModal();
  });
  document.getElementById('loginForm').addEventListener('submit', async e => {
    e.preventDefault();
    await doLogin();
  });
}

function openModal() {
  document.getElementById('modalBg').classList.remove('hidden');
  document.getElementById('loginErr').classList.add('hidden');
  document.getElementById('fUser').focus();
}

function closeModal() {
  document.getElementById('modalBg').classList.add('hidden');
}

async function doLogin() {
  const username = document.getElementById('fUser').value.trim();
  const password = document.getElementById('fPwd').value;
  const errEl    = document.getElementById('loginErr');
  const submitEl = document.getElementById('loginSubmit');

  if (!username || !password) {
    showLoginErr('请填写用户名和密码');
    return;
  }

  submitEl.textContent = '登录中…';
  submitEl.disabled    = true;
  errEl.classList.add('hidden');

  try {
    const res  = await fetch('/auth/login', {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ username, password }),
    });
    const data = await res.json();

    if (data.code === 200 && data.data?.token) {
      S.token       = data.data.token;
      S.username    = data.data.username;
      S.permissions = data.data.permissions || [];
      localStorage.setItem('sm_token',    S.token);
      localStorage.setItem('sm_username', S.username);
      localStorage.setItem('sm_perms',    JSON.stringify(S.permissions));
      renderAuthUI();
      closeModal();
      document.getElementById('fPwd').value = '';
    } else {
      showLoginErr(data.msg || '用户名或密码错误');
    }
  } catch {
    showLoginErr('网络错误，请稍后重试');
  } finally {
    submitEl.textContent = '登 录';
    submitEl.disabled    = false;
  }
}

function showLoginErr(msg) {
  const el = document.getElementById('loginErr');
  el.textContent = msg;
  el.classList.remove('hidden');
}

/* ================================================================
   图片上传 & 预览
   ================================================================ */
function initUpload() {
  setupZone({
    inputId:   'clothesInput',
    zoneId:    'clothesZone',
    phId:      'clothesPh',
    gridId:    'clothesGrid',
    fileList:  S.clothesFiles,
    max:       3,
  });
  setupZone({
    inputId:   'refInput',
    zoneId:    'refZone',
    phId:      'refPh',
    gridId:    'refGrid',
    fileList:  S.refFiles,
    max:       3,
  });
}

function setupZone({ inputId, zoneId, phId, gridId, fileList, max }) {
  const input = document.getElementById(inputId);
  const zone  = document.getElementById(zoneId);
  const ph    = document.getElementById(phId);
  const grid  = document.getElementById(gridId);

  // 点击上传区域 → 打开文件选择（避免点到删除按钮时触发）
  zone.addEventListener('click', e => {
    if (!e.target.closest('.uitem-del')) input.click();
  });

  input.addEventListener('change', e => {
    addFiles(e.target.files, fileList, max, grid, ph);
    input.value = '';
  });

  // 拖拽上传
  zone.addEventListener('dragover',  e => { e.preventDefault(); zone.classList.add('drag-over'); });
  zone.addEventListener('dragleave', ()  => zone.classList.remove('drag-over'));
  zone.addEventListener('drop', e => {
    e.preventDefault();
    zone.classList.remove('drag-over');
    addFiles(e.dataTransfer.files, fileList, max, grid, ph);
  });
}

function addFiles(fileListRaw, fileList, max, grid, ph) {
  const remaining = max - fileList.length;
  if (remaining <= 0) { showToast(`最多上传 ${max} 张`); return; }

  let added = 0;
  for (const file of fileListRaw) {
    if (added >= remaining) break;
    if (!file.type.startsWith('image/')) continue;
    fileList.push(file);
    appendPreview(file, fileList, fileList.length - 1, grid, ph);
    added++;
  }
  if (fileListRaw.length > remaining) {
    showToast(`最多还能上传 ${remaining} 张`);
  }
  syncPlaceholder(ph, fileList.length);
  refreshHint();
}

function appendPreview(file, fileList, index, grid, ph) {
  const reader = new FileReader();
  reader.onload = e => {
    const item = document.createElement('div');
    item.className = 'uitem';
    item.dataset.index = String(index);
    item.innerHTML = `
      <img src="${e.target.result}" alt="预览图">
      <button class="uitem-del" title="删除">×</button>
    `;
    item.querySelector('.uitem-del').addEventListener('click', ev => {
      ev.stopPropagation();
      fileList.splice(parseInt(item.dataset.index), 1);
      rebuildGrid(fileList, grid, ph);
      refreshHint();
    });
    grid.appendChild(item);
    syncPlaceholder(ph, fileList.length);
  };
  reader.readAsDataURL(file);
}

function rebuildGrid(fileList, grid, ph) {
  grid.innerHTML = '';
  fileList.forEach((f, i) => appendPreview(f, fileList, i, grid, ph));
  syncPlaceholder(ph, fileList.length);
}

function syncPlaceholder(ph, count) {
  if (count > 0) {
    ph.style.visibility = 'hidden';
  } else {
    ph.style.visibility = 'visible';
  }
}

/* ================================================================
   图片压缩（长边 ≤ 1200px，JPEG quality 0.85）
   ================================================================ */
function compressImage(file, maxLongEdge = 1200, quality = 0.85) {
  return new Promise(resolve => {
    const reader = new FileReader();
    reader.onload = e => {
      const img = new Image();
      img.onload = () => {
        const longEdge = Math.max(img.width, img.height);
        if (longEdge <= maxLongEdge) { resolve(file); return; }
        const scale   = maxLongEdge / longEdge;
        const canvas  = document.createElement('canvas');
        canvas.width  = Math.round(img.width  * scale);
        canvas.height = Math.round(img.height * scale);
        canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
        canvas.toBlob(blob => {
          const name = (file.name || 'image').replace(/\.[^.]+$/, '.jpg');
          resolve(new File([blob], name, { type: 'image/jpeg' }));
        }, 'image/jpeg', quality);
      };
      img.src = e.target.result;
    };
    reader.readAsDataURL(file);
  });
}

/* ================================================================
   换装生成
   ================================================================ */
function initGenerate() {
  document.getElementById('genBtn').addEventListener('click',   onGenerate);
  document.getElementById('regenBtn').addEventListener('click', onGenerate);
  document.getElementById('saveBtn').addEventListener('click',  onSave);
}

function refreshHint() {
  const hint = document.getElementById('genHint');
  if (!S.token) {
    hint.textContent = '登录后即可使用 AI 换装功能';
    return;
  }
  if (S.clothesFiles.length === 0) {
    hint.textContent = '请先上传想穿的衣服图片';
    return;
  }
  if (S.refFiles.length === 0) {
    hint.textContent = '请上传参考穿搭图片';
    return;
  }
  hint.textContent = '点击开始，AI 秒出试穿效果';
}

async function onGenerate() {
  if (!S.token) { openModal(); return; }
  if (S.clothesFiles.length === 0) { showToast('请先上传想穿的衣服图片'); return; }
  if (S.refFiles.length   === 0) { showToast('请上传参考穿搭图片');      return; }

  const style  = document.querySelector('input[name="style"]:checked')?.value  || '休闲';
  const scene  = document.querySelector('input[name="scene"]:checked')?.value  || '日常出行';
  const season = document.querySelector('input[name="season"]:checked')?.value || '春季';

  setLoading(true, '正在压缩图片…', 5);
  document.getElementById('resultSection').classList.add('hidden');

  try {
    // ── 1. 压缩图片 ───────────────────────────────────
    const clothCompressed = await compressImage(S.clothesFiles[0]);
    const refCompressed   = await compressImage(S.refFiles[0]);

    // ── 2. 提交任务，立即拿回 taskId ──────────────────
    setLoadingStep('正在提交任务…', 15);
    const fd = new FormData();
    fd.append('style', style); fd.append('scene', scene); fd.append('season', season);
    fd.append('clothesFiles',   clothCompressed, clothCompressed.name);
    fd.append('referenceFiles', refCompressed,   refCompressed.name);

    const submitRes = await fetch('/tryon/submit', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${S.token}` },
      body: fd,
    });
    if (submitRes.status === 401) { handleExpired(); return; }
    const submitData = await submitRes.json();
    if (submitData.code !== 200) throw new Error(submitData.msg || '提交失败');

    const taskId = submitData.data?.taskId;
    if (!taskId) throw new Error('未获取到任务ID');

    // ── 3. 轮询结果（每 2s，最多 60s）────────────────
    setLoadingStep('AI 换装中…', 25);
    const result = await pollResult(taskId);
    renderResult(result, style, scene, season);

  } catch (e) {
    showToast(e.message || '请求失败，请稍后重试');
  } finally {
    setLoading(false);
  }
}

/* 轮询 /tryon/result/{taskId}，返回最终结果或抛出错误 */
async function pollResult(taskId) {
  const MAX_POLLS = 30;   // 最多 30 次 × 2s = 60s
  for (let i = 0; i < MAX_POLLS; i++) {
    await new Promise(r => setTimeout(r, 2000));

    // 进度条：从 25% 爬到 92%
    const pct = Math.min(92, 25 + Math.round((i + 1) / MAX_POLLS * 67));
    const steps = ['AI 换装中…', '上传图片中…', '模型推理中…', '即将完成…'];
    setLoadingStep(steps[Math.min(i, steps.length - 1)], pct);

    const res  = await fetch(`/tryon/result/${taskId}`, {
      headers: { 'Authorization': `Bearer ${S.token}` },
    });
    if (res.status === 401) { handleExpired(); throw new Error('已登出'); }
    const data = await res.json();
    if (data.code !== 200) throw new Error(data.msg || '查询失败');

    const d = data.data || {};
    if (d.status === 'done')   { setLoadingStep('完成！', 100); return d; }
    if (d.status === 'failed') throw new Error(d.message || '生成失败，请重试');
    // status === 'pending' → 继续等
  }
  throw new Error('生成超时（>60s），请稍后重试');
}

function handleExpired() {
  setLoading(false);
  doLogout();
  showToast('登录已过期，请重新登录');
  openModal();
}

/* ================================================================
   结果渲染
   ================================================================ */
function renderResult(data, style, scene, season) {
  const imgs  = data.recommendImgs || data.imageUrls || [];
  const desc  = data.recommendText || `${style}风格试衣效果已生成`;

  S.resultImgUrls = imgs;

  // 图片区域
  const imgArea = document.getElementById('resultImgs');
  imgArea.innerHTML = '';
  if (imgs.length === 0) {
    imgArea.innerHTML = '<div style="padding:48px;text-align:center;color:var(--text-3);width:100%">暂无图片返回</div>';
  } else {
    imgs.forEach(url => {
      const item = document.createElement('div');
      item.className = 'result-img-item';
      const img = new Image();
      img.src = url;
      img.alt = '换装效果图';
      item.appendChild(img);
      imgArea.appendChild(item);
    });
  }

  // 描述
  document.getElementById('resultDesc').textContent = desc;

  // 耗时标签
  const chips = document.getElementById('resultChips');
  chips.innerHTML = '';
  const stats = [
    data.uploadSecond   != null ? `📤 上传 ${data.uploadSecond}s`   : null,
    data.generateSecond != null ? `🎨 生图 ${data.generateSecond}s` : null,
    data.totalSecond    != null ? `⏱ 总耗时 ${data.totalSecond}s`  : null,
    data.tokenUsage     != null ? `🔢 Token ${data.tokenUsage}`     : null,
  ].filter(Boolean);

  stats.forEach(t => {
    const c = document.createElement('span');
    c.className   = 'chip';
    c.textContent = t;
    chips.appendChild(c);
  });

  // 展示并滚动到结果
  const section = document.getElementById('resultSection');
  section.classList.remove('hidden');
  setTimeout(() => section.scrollIntoView({ behavior: 'smooth', block: 'start' }), 80);

  if (data.recordId) showFeedbackTrigger(data.recordId);
}

function onSave() {
  if (S.resultImgUrls.length === 0) return;
  S.resultImgUrls.forEach(url => window.open(url, '_blank'));
}

/* ================================================================
   工具函数
   ================================================================ */
function setLoading(on, stepText, pct) {
  document.getElementById('loadingWrap').classList.toggle('hidden', !on);
  document.getElementById('genBtn').disabled = on;
  document.getElementById('genBtnText').textContent = on ? '生成中…' : '一键换装';
  if (on) setLoadingStep(stepText || '正在压缩图片…', pct || 0);
}

function setLoadingStep(text, pct) {
  const stepEl = document.getElementById('loadingStep');
  const fillEl = document.getElementById('progressFill');
  const pctEl  = document.getElementById('progressPct');
  if (stepEl) stepEl.textContent  = text;
  if (fillEl) fillEl.style.width  = pct + '%';
  if (pctEl)  pctEl.textContent   = pct + '%';
}

let toastTimer;
function showToast(msg) {
  const el   = document.getElementById('toast');
  document.getElementById('toastMsg').textContent = msg;
  el.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), 4000);
}

function ext(file) {
  const name = file.name || 'img';
  const idx  = name.lastIndexOf('.');
  return idx >= 0 ? name.slice(idx + 1) : 'jpg';
}

/* ================================================================
   反馈面板
   ================================================================ */
let _currentRecordId = null;

function showFeedbackTrigger(recordId) {
  _currentRecordId = recordId;
  const btn = document.getElementById('fbTriggerBtn');
  if (btn) btn.classList.remove('hidden');
}

document.getElementById('fbTriggerBtn')?.addEventListener('click', () => {
  document.getElementById('feedbackWrap')?.classList.toggle('hidden');
});

// 语音输入（Web Speech API，仅普通话）
(function initMic() {
  const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
  const micBtn = document.getElementById('fbMicBtn');
  if (!micBtn) return;
  if (!SpeechRec) { micBtn.style.display = 'none'; return; }
  const rec = new SpeechRec();
  rec.lang = 'zh-CN';
  rec.interimResults = false;
  rec.onresult = e => {
    const text = e.results[0][0].transcript;
    const input = document.getElementById('fbExtraText');
    if (input) input.value = (input.value ? input.value + ' ' : '') + text;
  };
  rec.onerror = () => showToast('语音识别失败，请重试或手动输入');
  micBtn.addEventListener('mousedown', () => { micBtn.classList.add('mic-active'); try { rec.start(); } catch(e){} });
  micBtn.addEventListener('mouseup',   () => { micBtn.classList.remove('mic-active'); try { rec.stop(); } catch(e){} });
  micBtn.addEventListener('touchstart', e => { e.preventDefault(); micBtn.classList.add('mic-active'); try { rec.start(); } catch(e2){} });
  micBtn.addEventListener('touchend',   e => { e.preventDefault(); micBtn.classList.remove('mic-active'); try { rec.stop(); } catch(e2){} });
})();

document.getElementById('fbSubmitBtn')?.addEventListener('click', async () => {
  if (!_currentRecordId) return;
  const checked = [...document.querySelectorAll('#feedbackWrap .fb-tag input:checked')].map(i => i.value);
  const extra = document.getElementById('fbExtraText')?.value || '';
  const token = S.token || '';

  const params = new URLSearchParams();
  params.append('recordId', _currentRecordId);
  checked.forEach(t => params.append('tagCodes', t));
  if (extra.trim()) params.append('extraText', extra.trim());

  try {
    await fetch('/tryon/feedback', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params
    });
    showToast('反馈已提交，正在结合偏好重新生成…');
  } catch(e) {
    showToast('反馈提交失败，请重试');
    return;
  }

  // 重置反馈面板
  document.querySelectorAll('#feedbackWrap .fb-tag input').forEach(i => i.checked = false);
  if (document.getElementById('fbExtraText')) document.getElementById('fbExtraText').value = '';
  document.getElementById('feedbackWrap')?.classList.add('hidden');
  document.getElementById('fbTriggerBtn')?.classList.add('hidden');

  // 触发重新生成
  document.getElementById('genBtn')?.click();
});
