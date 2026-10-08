import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TradesService } from './trades.service';

@Component({
  selector: 'app-trades',
  template: `
    <div class="trades-container">
      <main class="trades-main" role="main">
        <div class="trades-header">
          <h1>Trade History</h1>
          <p class="subtitle">View all your completed and pending trades</p>
        </div>

        <!-- Stats Section -->
        <div class="stats-section">
          <div class="stat-card">
            <div class="stat-label">Total Trades</div>
            <div class="stat-value">{{ getCompletedTrades() }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">Total Volume</div>
            <div class="stat-value">{{ formatCurrency(getTotalVolume()) }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">Total Commissions</div>
            <div class="stat-value">{{ formatCurrency(getTotalCommissions()) }}</div>
          </div>
        </div>

        <!-- Trades Table -->
        <div class="trades-card">
          <div class="table-header">
            <h2>Recent Trades</h2>
            <div class="filter-controls">
              <select [value]="selectedFilter()" (change)="onFilterChange($event)" class="filter-select" aria-label="Filter trades by status">
                <option value="all">All Trades</option>
                <option value="completed">Completed</option>
                <option value="pending">Pending</option>
                <option value="cancelled">Cancelled</option>
              </select>
            </div>
          </div>

          <div class="table-wrapper">
            <table role="grid" aria-label="Trade history table">
              <thead>
                <tr>
                  <th scope="col">Trade ID</th>
                  <th scope="col">Symbol</th>
                  <th scope="col">Company</th>
                  <th scope="col">Action</th>
                  <th scope="col">Quantity</th>
                  <th scope="col">Price</th>
                  <th scope="col">Total</th>
                  <th scope="col">Date & Time</th>
                  <th scope="col">Status</th>
                </tr>
              </thead>
              <tbody>
                @for (trade of getFilteredTrades(); track trade.id) {
                  <tr [class.buy-row]="trade.action === 'buy'" [class.sell-row]="trade.action === 'sell'">
                    <td><strong>{{ trade.id }}</strong></td>
                    <td>
                      <span class="symbol-badge">{{ trade.symbol }}</span>
                    </td>
                    <td>{{ trade.company }}</td>
                    <td>
                      <span [class.action-buy]="trade.action === 'buy'" [class.action-sell]="trade.action === 'sell'" class="action-badge">
                        {{ trade.action | uppercase }}
                      </span>
                    </td>
                     <td class="text-right mono-value">{{ trade.quantity }}</td>
                     <td class="text-right mono-value">{{ formatCurrency(trade.price) }}</td>
                     <td class="text-right mono-value"><strong>{{ formatCurrency(trade.total) }}</strong></td>
                     <td class="text-muted mono-value">{{ trade.date }}<br/>{{ trade.time }}</td>
                    <td>
                      <span [class.status-completed]="trade.status === 'completed'"
                            [class.status-pending]="trade.status === 'pending'"
                            [class.status-cancelled]="trade.status === 'cancelled'"
                            class="status-badge">
                        {{ trade.status | titlecase }}
                      </span>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          @if (getFilteredTrades().length === 0) {
            <div class="no-trades">
              <p>No trades found with the selected filter.</p>
            </div>
          }
        </div>
      </main>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 100vh;
      background: linear-gradient(135deg, #f8f9fa 0%, #f0f1f3 100%);
    }

    .trades-container {
      width: 100%;
      display: flex;
      flex-direction: column;
    }

    .trades-main {
      flex: 1;
      padding: 2rem;
      max-width: 1400px;
      margin: 0 auto;
      width: 100%;
    }

    .trades-header {
      margin-bottom: 2rem;
    }

    .trades-header h1 {
      color: #006044;
      font-size: 2rem;
      margin: 0 0 0.5rem 0;
    }

    .subtitle {
      color: #666;
      margin: 0;
    }

    .stats-section {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1.5rem;
      margin-bottom: 2rem;
    }

    .stat-card {
      background: white;
      padding: 1.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      border-left: 4px solid #76A923;
    }

    .stat-label {
      color: #666;
      font-size: 0.875rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      margin-bottom: 0.5rem;
    }

    .stat-value {
      color: #006044;
      font-size: 1.75rem;
      font-weight: 700;
       font-family: Consolas, 'SFMono-Regular', Menlo, Monaco, monospace;
    }

    .trades-card {
      background: white;
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      overflow: hidden;
    }

    .table-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1.5rem;
      border-bottom: 2px solid #76A923;
    }

    .table-header h2 {
      margin: 0;
      color: #006044;
      font-size: 1.25rem;
    }

    .filter-controls {
      display: flex;
      gap: 1rem;
    }

    .filter-select {
      padding: 0.5rem 1rem;
      border: 1px solid #ddd;
      border-radius: 4px;
      background-color: white;
      color: #006044;
      font-weight: 500;
      cursor: pointer;
      transition: border-color 0.2s ease;
    }

    .filter-select:hover {
      border-color: #76A923;
    }

    .filter-select:focus-visible {
      outline: 3px solid #76A923;
      outline-offset: 2px;
    }

    .table-wrapper {
      overflow-x: auto;
    }

    table {
      width: 100%;
      border-collapse: collapse;
    }

    thead {
      background-color: #f8f9fa;
      border-bottom: 2px solid #e0e0e0;
    }

    th {
      padding: 1rem;
      text-align: left;
      font-weight: 600;
      color: #006044;
      font-size: 0.875rem;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    td {
      padding: 1rem;
      border-bottom: 1px solid #e0e0e0;
      color: #333;
    }

    tbody tr:hover {
      background-color: #f8f9fa;
    }

    tbody tr.buy-row {
      border-left: 3px solid #27ae60;
    }

    tbody tr.sell-row {
      border-left: 3px solid #e74c3c;
    }

    .symbol-badge {
      background-color: #e8f5e9;
      color: #006044;
      padding: 0.25rem 0.75rem;
      border-radius: 4px;
      font-weight: 600;
      font-size: 0.875rem;
    }

    .action-badge {
      padding: 0.25rem 0.75rem;
      border-radius: 4px;
      font-weight: 600;
      font-size: 0.875rem;
    }

    .action-buy {
      background-color: #d4edda;
      color: #155724;
    }

    .action-sell {
      background-color: #f8d7da;
      color: #721c24;
    }

    .status-badge {
      padding: 0.25rem 0.75rem;
      border-radius: 4px;
      font-weight: 600;
      font-size: 0.875rem;
    }

    .status-completed {
      background-color: #d4edda;
      color: #155724;
    }

    .status-pending {
      background-color: #fff3cd;
      color: #856404;
    }

    .status-cancelled {
      background-color: #f8d7da;
      color: #721c24;
    }

    .text-right {
      text-align: right;
    }

    .text-muted {
      color: #999;
      font-size: 0.875rem;
    }

    .no-trades {
      padding: 2rem;
      text-align: center;
      color: #666;
    }

    @media (max-width: 1024px) {
      .stats-section {
        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      }
    }

    @media (max-width: 768px) {
      .trades-main {
        padding: 1rem;
      }

      .trades-header h1 {
        font-size: 1.5rem;
      }

      .table-header {
        flex-direction: column;
        gap: 1rem;
        align-items: flex-start;
      }

      .table-wrapper {
        overflow-x: auto;
      }

      table {
        font-size: 0.85rem;
      }

      th, td {
        padding: 0.75rem 0.5rem;
      }
    }
  `],
  imports: [CommonModule],
})
export class TradesComponent {
  private tradesService = inject(TradesService);

  selectedFilter = signal<'all' | 'completed' | 'pending' | 'cancelled'>('all');

  getTotalCommissions() {
    return this.tradesService.getTotalCommissions();
  }

  getTotalVolume() {
    return this.tradesService.getTotalVolume();
  }

  getCompletedTrades() {
    return this.tradesService.getCompletedTrades();
  }

  getFilteredTrades() {
    const filter = this.selectedFilter();
    const trades = this.tradesService.getTrades();

    if (filter === 'all') {
      return trades();
    }

    return trades().filter((trade) => trade.status === filter);
  }

  onFilterChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.selectedFilter.set(value as 'all' | 'completed' | 'pending' | 'cancelled');
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  }
}

