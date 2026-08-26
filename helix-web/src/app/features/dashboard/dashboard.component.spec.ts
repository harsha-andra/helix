import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DashboardSummary } from '../../core/models';
import { DashboardApiService } from '../../core/services/dashboard-api.service';
import { DashboardComponent } from './dashboard.component';

const SUMMARY: DashboardSummary = {
  openClaims: 12,
  awaitingReview: 5,
  totalReservedAmount: 250_000,
  avgCycleTimeDays: 8.4,
  byStatus: [
    { status: 'SUBMITTED', count: 4 },
    { status: 'UNDER_REVIEW', count: 5 },
    { status: 'PAID', count: 10 },
  ],
  recentActivity: [
    {
      id: 'EVT-1',
      entityType: 'CLAIM',
      entityId: 'CLM-1',
      action: 'CLAIM_SUBMITTED',
      actor: 'Jane Doe',
      occurredAt: new Date().toISOString(),
      detail: 'Claim submitted via online portal.',
    },
  ],
};

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let apiSpy: jasmine.SpyObj<DashboardApiService>;

  async function setup(response: 'success' | 'error') {
    apiSpy = jasmine.createSpyObj<DashboardApiService>('DashboardApiService', ['getSummary']);
    apiSpy.getSummary.and.returnValue(
      response === 'success' ? of(SUMMARY) : throwError(() => new Error('network down')),
    );

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [{ provide: DashboardApiService, useValue: apiSpy }, provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  }

  it('creates and requests the dashboard summary on init', async () => {
    await setup('success');
    expect(fixture.componentInstance).toBeTruthy();
    expect(apiSpy.getSummary).toHaveBeenCalled();
  });

  it('exposes KPI cards derived from the loaded summary', async () => {
    await setup('success');
    const kpis = fixture.componentInstance.kpis();

    expect(fixture.componentInstance.loading()).toBeFalse();
    expect(kpis.length).toBe(4);
    expect(kpis.find((k) => k.label === 'Open Claims')?.value).toBe('12');
    expect(kpis.find((k) => k.label === 'Avg Cycle Time')?.value).toBe('8.4d');
  });

  it('renders the empty/error state and allows retrying when the summary request fails', async () => {
    await setup('error');

    expect(fixture.componentInstance.error()).toContain('Could not load');
    expect(fixture.componentInstance.loading()).toBeFalse();

    apiSpy.getSummary.and.returnValue(of(SUMMARY));
    fixture.componentInstance.load();
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).toBeNull();
    expect(fixture.componentInstance.summary()).toEqual(SUMMARY);
  });

  it('computes bar width proportional to the largest status count', async () => {
    await setup('success');
    const component = fixture.componentInstance;

    expect(component.barWidth(10)).toBe('100%');
    expect(component.barWidth(5)).toBe('50%');
  });
});
