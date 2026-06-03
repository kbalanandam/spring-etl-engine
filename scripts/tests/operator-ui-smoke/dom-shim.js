class FakeElement {
  constructor(tagName, id = "") {
	this.tagName = tagName.toUpperCase();
	this.id = id;
	this.hidden = false;
	this.disabled = false;
	this.value = "";
	this.checked = false;
	this.textContent = "";
	this._innerHTML = "";
	this.className = "";
	this.title = "";
	this.dataset = {};
	this.children = [];
	this._listeners = new Map();
  }

  get innerHTML() {
	return this._innerHTML;
  }

  set innerHTML(value) {
	this._innerHTML = String(value);
	this.children = [];
  }

  addEventListener(type, listener) {
	if (!this._listeners.has(type)) {
	  this._listeners.set(type, []);
	}
	this._listeners.get(type).push(listener);
  }

  dispatch(type, overrides = {}) {
	const listeners = this._listeners.get(type) || [];
	const event = { target: this, ...overrides };
	listeners.forEach((listener) => listener(event));
  }

  appendChild(child) {
	this.children.push(child);
	return child;
  }
}

export function installDom(ids) {
  const previousDocument = globalThis.document;
  const previousLocation = globalThis.location;

  const elements = new Map();
  ids.forEach((id) => {
	elements.set(id, new FakeElement("div", id));
  });

  const document = {
	getElementById(id) {
	  return elements.get(id) || null;
	},
	createElement(tagName) {
	  return new FakeElement(tagName);
	},
  };

  const location = { hash: "" };

  globalThis.document = document;
  globalThis.location = location;

  return {
	elements,
	restore() {
	  globalThis.document = previousDocument;
	  globalThis.location = previousLocation;
	},
  };
}


