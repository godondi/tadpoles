import { Component } from '@angular/core';

@Component({
  selector: 'app-analytics-overview',
  template: `
    <main class="workspace-page" role="main">
      <h1>Analytics overview</h1>
      <p>Reporting metrics are unavailable in this environment because a separate analytics data store is not configured.</p>
      <p>When enabled, this page will display aggregate submitted, accepted, filled, and rejected counts with freshness metadata.</p>
    </main>
  `,
})
export class AnalyticsOverviewComponent {}
