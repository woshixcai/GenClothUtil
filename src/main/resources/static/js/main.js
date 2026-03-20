/* ================================================================
   StyleMe · main.js
   功能：主题切换 / JWT 登录 / 图片上传预览 / AI 换装请求 / 结果展示
   ================================================================ */

/* ── 全局状态 ─────────────────────────────────────────────────── */
const S = {
  token:         localStorage.getItem('sm_token')    || '',
  username:      localStorage.getItem('sm_username') || '',
  theme:         localStorage.getItem('sm_theme')    || 'gentle-luxury',
  clothesFiles:  [],
  refFiles:      [],
  resultImgUrls: [],
};

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
    document.getElementById('userName').textContent  = S.username;
    document.getElementById('userAvatar').textContent = S.username.charAt(0).toUpperCase();
  }
  refreshHint();
}

function doLogout() {
  S.token    = '';
  S.username = '';
  localStorage.removeItem('sm_token');
  localStorage.removeItem('sm_username');
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
      S.token    = data.data.token;
      S.username = data.data.username;
      localStorage.setItem('sm_token',    S.token);
      localStorage.setItem('sm_username', S.username);
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

  if (S.clothesFiles.length === 0) {
    showToast('请先上传想穿的衣服图片'); return;
  }
  if (S.refFiles.length === 0) {
    showToast('请上传参考穿搭图片'); return;
  }

  const style  = document.querySelector('input[name="style"]:checked')?.value  || '休闲';
  const scene  = document.querySelector('input[name="scene"]:checked')?.value  || '日常出行';
  const season = document.querySelector('input[name="season"]:checked')?.value || '春季';

  const fd = new FormData();
  fd.append('style',  style);
  fd.append('scene',  scene);
  fd.append('season', season);
  S.clothesFiles.forEach((f, i) => fd.append('clothesFiles', f, `clothes_${i}.${ext(f)}`));
  S.refFiles.forEach((f, i)      => fd.append('referenceFiles', f, `ref_${i}.${ext(f)}`));

  setLoading(true);
  document.getElementById('resultSection').classList.add('hidden');

  try {
    const res = await fetch('/tryon/recommend', {
      method:  'POST',
      headers: { 'Authorization': `Bearer ${S.token}` },
      body:    fd,
    });

    if (res.status === 401) {
      handleExpired(); return;
    }
    if (!res.ok) throw new Error(`请求失败 (${res.status})`);

    const data = await res.json();
    renderResult(data, style, scene, season);
  } catch (e) {
    showToast(e.message || '请求失败，请稍后重试');
  } finally {
    setLoading(false);
  }
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
function setLoading(on) {
  document.getElementById('loadingWrap').classList.toggle('hidden', !on);
  document.getElementById('genBtn').disabled = on;
  if (on) {
    document.getElementById('genBtnText').textContent = '生成中…';
  } else {
    document.getElementById('genBtnText').textContent = '一键换装';
  }
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
