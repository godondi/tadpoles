import { Routes } from '@angular/router';
import { DashboardComponent } from './dashboard';
import { ProfileComponent } from './profile';
import { TradesComponent } from './trades';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'profile', component: ProfileComponent },
  { path: 'trades', component: TradesComponent },
  { path: '**', redirectTo: '' },
];
