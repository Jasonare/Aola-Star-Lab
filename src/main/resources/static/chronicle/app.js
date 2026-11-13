const get = id => document.getElementById(id);
const viewport = get("viewport");
const track = get("track");
const slider = get("slider");
const entries = get("entries");
const fields = get("fields");
const backgrounds = [...document.querySelectorAll(".backdrop")];
const page = document.querySelector(".chronicle-page");
const assetBase = new URL(page.dataset.assetBase, location.href);
const apiUrl = new URL(page.dataset.apiUrl, location.href);
const defaultBackground = "assets/sky.png";
const storageKey = "aola-chronicle-nodes-v1";
const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)");
let records = [];
let version = null;
let latestSnapshot = null;
let saving = false;
let legacy = null;
try {
  const raw = localStorage.getItem(storageKey);
  if (raw !== null) {
    get("legacy-notice").hidden = false;
    legacy = JSON.parse(raw);
    if (Array.isArray(legacy) && legacy.every(node => node && typeof node === "object" && typeof node.title === "string")) {
      get("import-legacy").hidden = false;
    } else {
      get("legacy-notice").textContent = "检测到旧本地数据，但格式无法识别；原数据未被修改。";
    }
  }
} catch {
  get("legacy-notice").hidden = false;
  get("legacy-notice").textContent = "无法读取或解析旧本地数据；原数据未被修改。";
}
function safeBackground(value) {
  if (!value) return true;
  if (/^assets\/[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*\.[A-Za-z0-9]+(?:#hue=-?[0-9]{1,3})?$/.test(value)) return true;
  if (/[\\"'\x00-\x1f\x7f-\x9f]/.test(value)) return false;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !!url.hostname && !url.username && !url.password;
  } catch { return false; }
}
async function readSnapshot() {
  const response = await fetch(apiUrl, { credentials: "same-origin", cache: "no-store", redirect: "error" });
  if (!response.ok) throw new Error(response.status === 401 || response.status === 403 ? "登录已失效或无权访问，请重新登录。" : `服务器返回 ${response.status}。`);
  const snapshot = await response.json();
  if (!Number.isSafeInteger(snapshot.version) || snapshot.version < 0 || !Array.isArray(snapshot.nodes) ||
      !snapshot.nodes.every(node => node && ["year", "title", "yabi", "description", "background"].every(key => typeof node[key] === "string"))) {
    throw new Error("服务器返回的数据格式无效。");
  }
  return snapshot;
}
async function loadShared() {
  get("retry-load").hidden = true;
  get("open-editor").disabled = true;
  get("load-status").textContent = "正在加载共享编年史…";
  try {
    const snapshot = await readSnapshot();
    records = snapshot.nodes;
    version = snapshot.version;
    render(0);
    get("load-status").textContent = "";
    get("open-editor").disabled = false;
  } catch (error) {
    get("load-status").textContent = `加载共享编年史失败：${error.message} 请重试。`;
    get("retry-load").hidden = false;
  }
}
get("retry-load").onclick = loadShared;
let selected = -1;
let positions = [];
let pointer = null;
let visibleLayer = 1;
let backgroundRequest = 0;
let currentBackground;
let scrollFrame;

for (let index = 0; index < 42; index++) {
  const mote = document.createElement("i");
  mote.className = "mote";
  mote.style.cssText = `left:${Math.random()*100}%;top:${Math.random()*100}%;width:${index%3+1}px;height:${index%3+1}px;--duration:${7+Math.random()*9}s;--delay:-${Math.random()*12}s`;
  get("dust").append(mote);
}

function changeBackground(node) {
  const requested = node?.background || defaultBackground;
  if (requested === currentBackground) return;
  currentBackground = requested;
  const request = ++backgroundRequest;
  const image = new Image();
  image.onload = () => {
    if (request !== backgroundRequest) return;
    const incoming = backgrounds[1 - visibleLayer];
    incoming.style.backgroundImage = `url(${JSON.stringify(image.src)})`;
    incoming.style.filter = new URL(image.src).hash.match(/^#hue=(-?\d{1,3})$/) ? `hue-rotate(${new URL(image.src).hash.slice(5)}deg)` : "";
    incoming.classList.add("visible");
    backgrounds[visibleLayer].classList.remove("visible");
    visibleLayer = 1 - visibleLayer;
  };
  image.onerror = () => {
    if (request === backgroundRequest && image.src !== new URL(defaultBackground, assetBase).href) image.src = new URL(defaultBackground, assetBase).href;
  };
  try {
    const url = new URL(requested, assetBase);
    image.src = safeBackground(requested) ? url.href : new URL(defaultBackground, assetBase).href;
  } catch {
    image.src = new URL(defaultBackground, assetBase).href;
  }
}

function layout(center = false) {
  const style = getComputedStyle(track);
  const gutter = parseFloat(style.getPropertyValue("--gutter"));
  const step = parseFloat(style.getPropertyValue("--step"));
  const required = 2 * gutter + Math.max(0, records.length - 1) * step;
  const width = Math.max(viewport.clientWidth, required);
  track.style.width = width + "px";
  positions = records.map((_, index) => (width - required) / 2 + gutter + index * step);
  [...entries.children].forEach((entry, index) => entry.style.left = positions[index] + "px");
  if (selected >= 0) {
    moveSlider(positions[selected]);
    if (center) centerNode("instant");
  }
}
function moveSlider(x) {
  slider.style.setProperty("--slider-x", x + "px");
}
function centerNode(behavior = reducedMotion.matches ? "instant" : "smooth") {
  viewport.scrollTo({ left: positions[selected] - viewport.clientWidth / 2, behavior });
}
function select(index, center = true) {
  if (!records.length) {
    selected = -1;
    get("position").textContent = "暂无节点";
    get("prev").disabled = get("next").disabled = true;
    changeBackground();
    return;
  }
  selected = Math.min(records.length - 1, Math.max(0, index));
  const node = records[selected];
  moveSlider(positions[selected]);
  [...entries.children].forEach((entry, entryIndex) => {
    const active = entryIndex === selected;
    entry.classList.toggle("active", active);
    entry.querySelector(".card").setAttribute("aria-pressed", active);
  });
  slider.setAttribute("aria-valuenow", selected + 1);
  slider.setAttribute("aria-valuetext", node.year + " " + node.title);
  get("position").textContent = `${node.year}　·　${selected + 1} / ${records.length}`;
  get("prev").disabled = selected === 0;
  get("next").disabled = selected === records.length - 1;
  changeBackground(node);
  if (center) centerNode();
}
function render(index) {
  entries.replaceChildren();
  records.forEach((node, index) => {
    const entry = document.createElement("div");
    entry.className = index % 2 ? "entry down" : "entry";
    const stem = document.createElement("span");
    stem.className = "stem";
    const pin = document.createElement("button");
    pin.className = "pin";
    pin.setAttribute("aria-label", node.year + " " + node.title);
    pin.addEventListener("click", () => select(index));
    const card = document.createElement("button");
    card.className = "card";
    card.title = [node.year + " " + node.title, node.yabi, node.description].filter(Boolean).join("\n");
    card.addEventListener("click", () => select(index));
    for (const [className, value] of [["year", node.year], ["title", node.title], ["yabi", node.yabi], ["desc", node.description]]) {
      const text = document.createElement(className === "title" ? "strong" : "span");
      text.className = className;
      text.textContent = value;
      card.append(text);
    }
    entry.append(stem, pin, card);
    entries.append(entry);
  });
  selected = -1;
  slider.hidden = get("rail").hidden = records.length === 0;
  get("empty").hidden = records.length > 0;
  slider.setAttribute("aria-valuemax", records.length);
  layout();
  select(index, false);
  if (selected >= 0) centerNode("instant");
}
function dragPosition() {
  const x = pointer.clientX - track.getBoundingClientRect().left - pointer.offset;
  return Math.max(positions[0], Math.min(positions.at(-1), x));
}
function scrollWhileDragging() {
  if (!pointer) return;
  const bounds = viewport.getBoundingClientRect();
  const nearLeft = Math.max(0, bounds.left + 55 - pointer.clientX);
  const nearRight = Math.max(0, pointer.clientX - bounds.right + 55);
  viewport.scrollLeft += Math.max(-20, Math.min(20, (nearRight - nearLeft) / 4));
  moveSlider(dragPosition());
  scrollFrame = requestAnimationFrame(scrollWhileDragging);
}
slider.addEventListener("pointerdown", event => {
  if (!event.isPrimary || event.button !== 0) return;
  const box = slider.getBoundingClientRect();
  pointer = { id: event.pointerId, clientX: event.clientX, offset: event.clientX - box.left - box.width / 2 };
  slider.setPointerCapture(event.pointerId);
  slider.classList.add("dragging");
  scrollWhileDragging();
});
slider.addEventListener("pointermove", event => {
  if (pointer?.id === event.pointerId) pointer.clientX = event.clientX;
});
function stopDrag(event) {
  if (pointer?.id !== event.pointerId) return;
  if (event.type === "pointerup") pointer.clientX = event.clientX;
  const x = dragPosition();
  moveSlider(x);
  slider.getBoundingClientRect();
  pointer = null;
  cancelAnimationFrame(scrollFrame);
  slider.classList.remove("dragging");
  const nearest = positions.reduce((best, position, index) => Math.abs(position - x) < Math.abs(positions[best] - x) ? index : best, 0);
  select(nearest);
}
for (const type of ["pointerup", "pointercancel", "lostpointercapture"]) slider.addEventListener(type, stopDrag);
slider.addEventListener("keydown", event => {
  const targets = { ArrowLeft: selected - 1, ArrowRight: selected + 1, Home: 0, End: records.length - 1 };
  if (!(event.key in targets)) return;
  event.preventDefault();
  select(targets[event.key]);
});
get("prev").onclick = () => select(selected - 1);
get("next").onclick = () => select(selected + 1);
window.addEventListener("resize", () => { if (!get("chronicle").hidden) layout(true); });

function updateRows() {
  [...fields.children].forEach((row, index) => {
    row.querySelector("legend").textContent = "节点 " + (index + 1);
    row.querySelector('[data-action="up"]').disabled = index === 0;
    row.querySelector('[data-action="down"]').disabled = index === fields.children.length - 1;
  });
}
function addRow(node = {}) {
  const row = get("node-form").content.firstElementChild.cloneNode(true);
  for (const input of row.querySelectorAll("[name]")) input.value = node[input.name] ?? "";
  fields.append(row);
  updateRows();
  return row;
}
function leaveEditor(index) {
  get("editor").hidden = true;
  get("chronicle").hidden = false;
  window.scrollTo(0, 0);
  render(index);
  get("open-editor").focus({preventScroll:true});
}
get("open-editor").onclick = () => {
  fields.replaceChildren();
  records.forEach(addRow);
  get("error").textContent = "";
  get("conflict-info").textContent = "";
  get("refresh-version").hidden = get("adopt-version").hidden = get("server-preview").hidden = true;
  latestSnapshot = null;
  get("chronicle").hidden = true;
  get("editor").hidden = false;
  window.scrollTo(0, 0);
  get("cancel").focus({preventScroll:true});
};
get("cancel").onclick = () => {
  if (confirm("返回时间轴将放弃编辑器中的未保存修改，确定返回吗？")) leaveEditor(selected);
};
get("import-legacy").onclick = () => {
  if (!confirm("导入会替换当前编辑器内容，但不会修改旧本地数据或自动保存到服务器。继续吗？")) return;
  fields.replaceChildren();
  legacy.forEach(addRow);
  get("error").textContent = "已导入旧本地节点，请检查内容并手动保存。";
};
get("refresh-version").onclick = async () => {
  saving = true;
  get("refresh-version").disabled = get("save").disabled = get("cancel").disabled = true;
  latestSnapshot = null;
  get("adopt-version").hidden = get("server-preview").hidden = true;
  try {
    latestSnapshot = await readSnapshot();
    get("conflict-info").textContent = `服务器当前版本为 ${latestSnapshot.version}。请先查看以下服务器内容；选择保留草稿后，下一次保存将用草稿替换这些内容，并再次检查版本。`;
    get("server-preview").textContent = JSON.stringify(latestSnapshot.nodes, null, 2);
    get("server-preview").hidden = false;
    get("adopt-version").hidden = false;
  } catch (error) {
    get("conflict-info").textContent = `获取最新版本失败：${error.message} 草稿仍在编辑器中。`;
  } finally {
    saving = false;
    get("refresh-version").disabled = get("save").disabled = get("cancel").disabled = false;
  }
};
get("adopt-version").onclick = () => {
  if (!latestSnapshot || !confirm("确定使用当前草稿替换刚查看的服务器内容？需要再次点击保存，服务器仍会检查版本。")) return;
  version = latestSnapshot.version;
  latestSnapshot = null;
  get("refresh-version").hidden = true;
  get("adopt-version").hidden = true;
  get("server-preview").hidden = true;
  get("conflict-info").textContent = "已选择以最新版本为基础，草稿尚未保存。";
  get("error").textContent = "";
};
get("add").onclick = () => addRow().querySelector("input").focus();
fields.addEventListener("click", event => {
  const action = event.target.dataset.action;
  if (!action) return;
  const row = event.target.closest("fieldset");
  if (action === "up" && row.previousElementSibling) fields.insertBefore(row, row.previousElementSibling);
  if (action === "down" && row.nextElementSibling) fields.insertBefore(row.nextElementSibling, row);
  if (action === "remove") row.remove();
  updateRows();
});
get("form").addEventListener("submit", async event => {
  event.preventDefault();
  if (saving || version === null) return;
  const updated = [...fields.children].map(row => Object.fromEntries(
    [...row.querySelectorAll("[name]")].map(input => [input.name, input.value.trim()])
  ));
  if (updated.some(node => !node.year || !node.title)) {
    get("error").textContent = "请填写每个节点的年份和标题。";
    return;
  }
  if (updated.length > 200 || updated.some(node => node.background.length > 2048 || !safeBackground(node.background))) {
    get("error").textContent = "节点不能超过 200 个；背景地址须为空、安全的 assets/ 路径或不含凭据的 HTTPS 地址，且不超过 2048 字符。";
    return;
  }
  saving = true;
  get("save").disabled = get("cancel").disabled = get("add").disabled = get("import-legacy").disabled = get("refresh-version").disabled = get("adopt-version").disabled = true;
  fields.disabled = true;
  const locked = [...fields.querySelectorAll("input, textarea, button")];
  locked.forEach(control => control.disabled = true);
  try {
    const response = await fetch(apiUrl, {
      method: "PUT", credentials: "same-origin", redirect: "error",
      headers: { "Content-Type": "application/json", [page.dataset.csrfHeader]: page.dataset.csrfToken },
      body: JSON.stringify({ version, nodes: updated })
    });
    if (response.status === 409) {
      latestSnapshot = null;
      get("refresh-version").hidden = false;
      get("adopt-version").hidden = get("server-preview").hidden = true;
      get("conflict-info").textContent = "";
      throw new Error("服务器内容已被其他用户修改。草稿仍在编辑器中，请查看最新版本并决定如何处理。");
    }
    if (!response.ok) throw new Error(response.status === 401 || response.status === 403 ? "登录已失效、无权保存或安全令牌已过期，请复制草稿后重新登录。" : `服务器返回 ${response.status}。`);
    const snapshot = await response.json();
    if (!Number.isSafeInteger(snapshot.version) || snapshot.version !== version + 1 || !Array.isArray(snapshot.nodes)) throw new Error("保存结果无法确认；请查看最新服务器版本后再决定如何处理。");
    const newIndex = updated.findIndex(node => !records.some(old => old.year === node.year && old.title === node.title));
    records = updated;
    version = snapshot.version;
    leaveEditor(newIndex >= 0 ? newIndex : selected);
  } catch (error) {
    get("refresh-version").hidden = false;
    get("error").textContent = `保存未确认：${error.message} 草稿仍在编辑器中。可查看最新服务器版本。`;
  } finally {
    saving = false;
    get("save").disabled = get("cancel").disabled = get("add").disabled = get("import-legacy").disabled = get("refresh-version").disabled = get("adopt-version").disabled = false;
    fields.disabled = false;
    locked.forEach(control => control.disabled = false);
    updateRows();
  }
});
render(0);
loadShared();
