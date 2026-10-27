import { Component, Input } from '@angular/core';
import { displayFact } from '../two-decimals.pipe';

@Component({
  selector: 'app-evidence-facts',
  template: `<dl class="evidence-facts">
    @for (fact of facts(); track fact.path) {
      <div>
        <dt>{{ fact.path }}</dt>
        <dd>{{ fact.value }}</dd>
      </div>
    }
  </dl>`,
})
export class EvidenceFactsComponent {
  @Input() value: unknown;
  facts() {
    const result: { path: string; value: string }[] = [];
    const visit = (value: unknown, path: string, depth: number) => {
      if (value !== null && typeof value === 'object' && depth < 6) {
        for (const [key, child] of Object.entries(value))
          visit(child, path ? `${path} / ${key}` : key, depth + 1);
      } else
        result.push({
          path,
          value:
            value === null || value === undefined ? 'Unavailable / not recorded' : String(displayFact(value, path)),
        });
    };
    visit(this.value, '', 0);
    return result;
  }
}
