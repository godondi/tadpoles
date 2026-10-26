import { TwoDecimalsPipe } from '../two-decimals.pipe';
import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Order } from '../../core/api/workspace-api.service';

@Component({
  selector: 'app-orders-table',
  imports: [TwoDecimalsPipe, RouterLink, DatePipe],
  template: ` @if (!orders().length) {
      <section class="empty">
        <h3>No orders to show</h3>
        <p>Orders matching these filters will appear here.</p>
      </section>
    } @else {
      <div
        class="table-wrapper"
        tabindex="0"
        aria-label="Order history, scroll horizontally on small screens"
      >
        <table>
          <caption>
            Recorded orders · newest first · dates as recorded
          </caption>
          <thead>
            <tr>
              <th scope="col">Reference</th>
              <th scope="col">Instrument</th>
              <th scope="col">Side</th>
              <th scope="col">Quantity</th>
              <th scope="col">Recorded status</th>
              <th scope="col">Date</th>
            </tr>
          </thead>
          <tbody>
            @for (order of orders(); track order.orderId) {
              <tr>
                <td>
                  <a [routerLink]="['/app/orders', order.orderId]">#{{ order.orderId }}</a>
                </td>
                <td>
                  {{ order.ticker }} <small>{{ order.currency }}</small>
                </td>
                <td>{{ order.side }}</td>
                <td class="mono-value">{{ (order.exactQuantity ?? order.quantity) | twoDecimals }}</td>
                <td>
                  <span class="status-label">{{ order.status }}</span
                  ><small>{{
                    order.recordKind === 'SELF_DIRECTED' ? 'Simulation' : 'Legacy record'
                  }}</small>
                </td>
                <td>
                  @if (order.submittedAt) {
                    {{ order.submittedAt | date: 'medium' : 'UTC' }} UTC
                  } @else {
                    {{ order.tradeDate }}
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }`,
})
export class OrdersTableComponent {
  orders = input.required<Order[]>();
}
