import { Component, input } from '@angular/core';
import { DatePipe, JsonPipe, KeyValuePipe } from '@angular/common';
import { EvidenceRow } from '../../core/api/operations-api.service';
import { DisplayFactPipe } from '../../shared/two-decimals.pipe';

@Component({
  selector: 'app-evidence-table',
  imports: [DatePipe, JsonPipe, KeyValuePipe, DisplayFactPipe],
  template: `
    <div class="table-wrapper" tabindex="0" [attr.aria-label]="caption() + ', scroll horizontally'">
      <table>
        <caption>
          {{
            caption()
          }}
        </caption>
        <thead>
          <tr>
            <th>Reference</th>
            <th>Recorded (UTC)</th>
            <th>Retained facts</th>
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track row.reference) {
            <tr>
              <td>{{ row.reference }}</td>
              <td>
                {{ row.recordedAt ? (row.recordedAt | date: 'medium' : 'UTC') : 'Not recorded' }}
              </td>
              <td>
                <dl class="details evidence-details">
                  @for (field of row.values | keyvalue; track field.key) {
                    <div>
                      <dt>{{ label(field.key) }}</dt>
                      <dd>
                        @if (object(field.value)) {
                          @if (checks(field.value); as rules) {
                            @if (rules.length) {
                              <details>
                                <summary>Review recorded rule outcomes</summary>
                                <p>Skipped or inapplicable checks are not evaluated.</p>
                                <ul>
                                  @for (rule of rules; track $index) {
                                    <li>{{ rule.code.replaceAll('_', ' ') }}: {{ rule.result }}</li>
                                  }
                                </ul>
                              </details>
                            }
                          }
                          <details>
                            <summary>Show captured values</summary>
                            <pre class="evidence-json">{{ field.value | displayFact: field.key | json }}</pre>
                          </details>
                        } @else {
                          {{ field.value === null ? 'Not recorded' : (field.value | displayFact: field.key) }}
                        }
                      </dd>
                    </div>
                  }
                </dl>
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="3">No retained records in this section.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class EvidenceTableComponent {
  readonly rows = input.required<EvidenceRow[]>();
  readonly caption = input.required<string>();
  object(value: unknown) {
    return value !== null && typeof value === 'object';
  }
  checks(value: unknown): { code: string; result: string }[] {
    if (!value || typeof value !== 'object' || !('checks' in value)) return [];
    if (!Array.isArray(value.checks)) return [];
    return value.checks.filter(
      (rule): rule is { code: string; result: string } =>
        rule && typeof rule.code === 'string' && typeof rule.result === 'string',
    );
  }
  label(value: string) {
    return value.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase());
  }
}
