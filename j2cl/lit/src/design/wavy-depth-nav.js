import { LitElement, css, html } from "lit";

/**
 * <wavy-depth-nav> — F-0 (#1035) recipe for the depth-nav breadcrumb
 * (Inbox › Sample wave › Top thread). No plugin slot — purely chrome
 * for orienting the user inside a deep wave/thread structure.
 *
 * Property:
 *   - crumbs: Array<{ label: string, href?: string, current?: boolean }>
 */
export class WavyDepthNav extends LitElement {
  static properties = {
    crumbs: { attribute: false }
  };

  static styles = css`
    :host {
      display: block;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      letter-spacing: 0.04em;
    }
    nav {
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-2, 8px);
      flex-wrap: wrap;
    }
    a {
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      text-decoration: none;
      transition: color var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    a:hover,
    a:focus-visible {
      color: var(--wavy-signal-cyan, #22d3ee);
    }
    .crumb-current {
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
    }
    .sep {
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
      user-select: none;
    }
  `;

  constructor() {
    super();
    this.crumbs = [];
  }

  render() {
    const items = Array.isArray(this.crumbs) ? this.crumbs : [];
    return html`
      <nav aria-label="Breadcrumb">
        ${items.map((c, i) => {
          const isLast = i === items.length - 1;
          const sep = isLast
            ? null
            : html`<span class="sep" aria-hidden="true">›</span>`;
          if (c.current) {
            return html`<span class="crumb-current" aria-current="page"
                >${c.label}</span
              >${sep}`;
          }
          if (c.href) {
            return html`<a href=${c.href}>${c.label}</a>${sep}`;
          }
          return html`<span>${c.label}</span>${sep}`;
        })}
      </nav>
    `;
  }
}

if (!customElements.get("wavy-depth-nav")) {
  customElements.define("wavy-depth-nav", WavyDepthNav);
}
