const get = id => document.getElementById(id);
const viewport = get("viewport");
const track = get("track");
const slider = get("slider");
const entries = get("entries");
const fields = get("fields");
const backgrounds = [...document.querySelectorAll(".backdrop")];
const assetBase = new URL(document.querySelector(".chronicle-page").dataset.assetBase, location.href);
const defaultBackground = "assets/sky.png";
const storageKey = "aola-chronicle-nodes-v1";
const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)");
let records = nodes;
try {
  const saved = JSON.parse(localStorage.getItem(storageKey));
  if (Array.isArray(saved) && saved.every(node => node && typeof node.title === "string")) records = saved;
} catch {}
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
    image.src = ["http:", "https:"].includes(url.protocol) ? url.href : new URL(defaultBackground, assetBase).href;
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
  get("chronicle").hidden = true;
  get("editor").hidden = false;
  window.scrollTo(0, 0);
  get("cancel").focus({preventScroll:true});
};
get("cancel").onclick = () => leaveEditor(selected);
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
get("form").addEventListener("submit", event => {
  event.preventDefault();
  const updated = [...fields.children].map(row => Object.fromEntries(
    [...row.querySelectorAll("[name]")].map(input => [input.name, input.value.trim()])
  ));
  if (updated.some(node => !node.year || !node.title)) {
    get("error").textContent = "请填写每个节点的年份和标题。";
    return;
  }
  try {
    localStorage.setItem(storageKey, JSON.stringify(updated));
  } catch {
    get("error").textContent = "浏览器无法保存修改，请检查站点存储权限或可用空间。";
    return;
  }
  const newIndex = updated.findIndex(node => !records.some(old => old.year === node.year && old.title === node.title));
  records = updated;
  leaveEditor(newIndex >= 0 ? newIndex : selected);
});
render(0);
