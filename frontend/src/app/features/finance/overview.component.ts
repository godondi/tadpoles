import { Component } from '@angular/core';

@Component({
  selector: 'app-finance-overview',
  template: `
    <main class="workspace-page" role="main">
      <h1>Finance overview</h1>
      <p>Booked-fill and cash-movement totals are unavailable because reconciliation data and posting references are not persisted yet.</p>
    </main>
  `,
})
export class FinanceOverviewComponent {}
