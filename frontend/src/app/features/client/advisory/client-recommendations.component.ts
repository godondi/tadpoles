import { Component, computed, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RequestStateComponent } from '../../../shared/workspace/request-state.component';
import { ClientAdvisory } from './client-advisory-models';
import { hasPositiveDecimal } from '../../advisor/advisor-utils';

@Component({
  selector: 'app-client-recommendations',
  imports: [DatePipe, RequestStateComponent],
  template: `
    <section class="panel" aria-labelledby="client-recommendations-heading">
      <div class="section-heading">
        <h2 id="client-recommendations-heading">Advisor recommends</h2>
        @if (advisory(); as data) {
          @if (data.asOf) {
            <p class="as-of">As of {{ data.asOf | date: 'medium' : 'UTC' }} UTC</p>
          }
        }
      </div>
      <app-request-state
        [loading]="loading() && !advisory()"
        [error]="error()"
        [status]="status()"
        (retry)="retry.emit()"
      />
      @if (!error() && advisory(); as data) {
        @if (!visibleRecommendations().length) {
          <p class="empty">No recommendations are currently recorded.</p>
        } @else {
          <div class="table-wrapper" tabindex="0" aria-label="Advisor recommendations, scroll horizontally">
            <table>
              <caption>Current recommendations for instruments not held</caption>
              <thead>
                <tr>
                  <th scope="col">Ticker / Instrument</th>
                  <th scope="col">Company / instrument name</th>
                  <th scope="col">Reasoning</th>
                </tr>
              </thead>
              <tbody>
                @for (recommendation of visibleRecommendations(); track recommendation.recommendationId) {
                  <tr>
                    <th scope="row">{{ recommendation.ticker }}</th>
                    <td>{{ recommendation.instrumentName }}</td>
                    <td>{{ recommendation.reasoning }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }
    </section>
  `,
})
export class ClientRecommendationsComponent {
  readonly advisory = input<ClientAdvisory | null>(null);
  readonly positions = input<readonly { instrumentId: number; owned: string }[]>([]);
  readonly visibleRecommendations = computed(() => (this.advisory()?.recommendations ?? []).filter((item) => !this.positions().some((position) => position.instrumentId === item.instrumentId && hasPositiveDecimal(position.owned))));
  readonly loading = input(false);
  readonly error = input('');
  readonly status = input(0);
  readonly retry = output<void>();
}
