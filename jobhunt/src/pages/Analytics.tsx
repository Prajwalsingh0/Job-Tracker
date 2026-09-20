import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Analytics, STATUS_LABELS } from '@/types';
import { StatusBadge } from '@/components/ui/StatusBadge';
import {
  BarChart3, TrendingUp, Users, Trophy, CalendarClock, RefreshCw, AlertCircle, Building2,
} from 'lucide-react';

const RANGE_PRESETS = [
  { label: 'Last 3 months', months: 3 },
  { label: 'Last 6 months', months: 6 },
  { label: 'Last 12 months', months: 12 },
];

/** Charts are plain CSS bars: no charting dependency, responsive, and screen-reader friendly. */
const SERIES = [
  { key: 'applied' as const, label: 'Applied', color: 'bg-blue-500' },
  { key: 'interviews' as const, label: 'Interviews', color: 'bg-purple-500' },
  { key: 'offers' as const, label: 'Offers', color: 'bg-green-500' },
];

function rangeForMonths(months: number): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to);
  from.setMonth(from.getMonth() - months);
  from.setDate(1);
  return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

function MetricCard({ label, value, suffix }: { label: string; value: number | string; suffix?: string }) {
  return (
    <div className="bg-white rounded-xl p-5 border border-gray-200 shadow-sm">
      <p className="text-sm text-gray-500">{label}</p>
      <p className="text-3xl font-bold text-gray-900 mt-1">
        {value}
        {suffix && <span className="text-lg text-gray-500 ml-1">{suffix}</span>}
      </p>
    </div>
  );
}

function SkeletonBlock({ height }: { height: string }) {
  return <div className={`animate-pulse bg-gray-100 rounded-xl ${height}`} aria-hidden="true" />;
}

export function AnalyticsPage() {
  const [months, setMonths] = useState(6);
  const [data, setData] = useState<Analytics | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    try {
      setData(await api.analytics(rangeForMonths(months)));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load analytics');
    } finally {
      setIsLoading(false);
    }
  }, [months]);

  useEffect(() => {
    void load();
  }, [load]);

  const metrics = data?.metrics;
  const funnelMax = Math.max(...(data?.funnel.map((stage) => stage.count) ?? [1]), 1);
  const timelineMax = Math.max(
    ...(data?.timeline.flatMap((point) => [point.applied, point.interviews, point.offers]) ?? [1]),
    1,
  );
  const hasAnyData = (metrics?.total ?? 0) > 0;

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Analytics</h1>
          <p className="text-gray-500 mt-1">
            {data ? `${data.from} to ${data.to}` : 'How your job search is actually going'}
          </p>
        </div>
        <div className="flex items-center gap-2" role="group" aria-label="Date range">
          {RANGE_PRESETS.map((preset) => (
            <button
              key={preset.months}
              onClick={() => setMonths(preset.months)}
              aria-pressed={months === preset.months}
              className={`px-3 py-2 text-sm rounded-lg border transition-colors ${
                months === preset.months
                  ? 'bg-indigo-50 border-indigo-200 text-indigo-700 font-medium'
                  : 'bg-white border-gray-300 text-gray-600 hover:bg-gray-50'
              }`}
            >
              {preset.label}
            </button>
          ))}
          <button
            onClick={() => void load()}
            aria-label="Refresh analytics"
            className="p-2 border border-gray-300 rounded-lg bg-white hover:bg-gray-50"
          >
            <RefreshCw className={`w-4 h-4 text-gray-500 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-3 bg-red-50 text-red-600 p-4 rounded-lg text-sm">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span className="flex-1">{error}</span>
          <button onClick={() => void load()} className="underline font-medium">
            Retry
          </button>
        </div>
      )}

      {isLoading ? (
        <div className="space-y-6" aria-busy="true" aria-live="polite">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <SkeletonBlock height="h-28" />
            <SkeletonBlock height="h-28" />
            <SkeletonBlock height="h-28" />
            <SkeletonBlock height="h-28" />
          </div>
          <SkeletonBlock height="h-64" />
          <SkeletonBlock height="h-64" />
        </div>
      ) : !hasAnyData ? (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm px-6 py-16 text-center">
          <BarChart3 className="w-12 h-12 mx-auto mb-3 text-gray-300" />
          <p className="text-gray-600 font-medium">Nothing to chart yet</p>
          <p className="text-gray-400 text-sm mt-1">
            Add some jobs and this page will fill in with your funnel, rates and trends.
          </p>
        </div>
      ) : (
        <>
          {/* Headline metrics */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <MetricCard label="Applications" value={metrics!.applied} />
            <MetricCard label="Response rate" value={metrics!.responseRate.toFixed(1)} suffix="%" />
            <MetricCard label="Interview rate" value={metrics!.interviewRate.toFixed(1)} suffix="%" />
            <MetricCard label="Offer rate" value={metrics!.offerRate.toFixed(1)} suffix="%" />
          </div>

          {/* Funnel */}
          <section className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
            <div className="flex items-center gap-3 mb-5">
              <div className="p-2 rounded-lg bg-indigo-500">
                <TrendingUp className="w-5 h-5 text-white" />
              </div>
              <h2 className="font-semibold text-gray-900">Application funnel</h2>
            </div>
            <ul className="space-y-3">
              {data!.funnel.map((stage) => (
                <li key={stage.status} className="flex items-center gap-3">
                  <span className="w-28 flex-shrink-0 text-sm text-gray-600">
                    {STATUS_LABELS[stage.status]}
                  </span>
                  <div className="flex-1 bg-gray-100 rounded-full h-6 overflow-hidden">
                    <div
                      className="h-full bg-indigo-500 rounded-full transition-all duration-500"
                      style={{ width: `${(stage.count / funnelMax) * 100}%` }}
                    />
                  </div>
                  <span className="w-10 text-right text-sm font-medium text-gray-700 tabular-nums">
                    {stage.count}
                  </span>
                </li>
              ))}
            </ul>
            <p className="mt-4 text-xs text-gray-400">
              Counts show where your applications currently sit, not how many reached each stage.
            </p>
          </section>

          {/* Monthly trend */}
          <section className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
            <div className="flex items-center gap-3 mb-5">
              <div className="p-2 rounded-lg bg-purple-500">
                <BarChart3 className="w-5 h-5 text-white" />
              </div>
              <h2 className="font-semibold text-gray-900">Monthly activity</h2>
              <div className="ml-auto flex items-center gap-3">
                {SERIES.map((series) => (
                  <span key={series.key} className="flex items-center gap-1.5 text-xs text-gray-500">
                    <span className={`w-2.5 h-2.5 rounded-sm ${series.color}`} />
                    {series.label}
                  </span>
                ))}
              </div>
            </div>
            <div className="flex items-end gap-4 h-48 overflow-x-auto pb-2">
              {data!.timeline.map((point) => (
                <div key={point.period} className="flex flex-col items-center gap-2 flex-shrink-0">
                  <div className="flex items-end gap-1 h-40">
                    {SERIES.map((series) => {
                      const value = point[series.key];
                      return (
                        <div
                          key={series.key}
                          role="img"
                          aria-label={`${point.period} ${series.label}: ${value}`}
                          title={`${series.label}: ${value}`}
                          className={`w-4 rounded-t ${series.color} ${value === 0 ? 'bg-gray-200' : ''}`}
                          style={{ height: `${value === 0 ? 2 : Math.max((value / timelineMax) * 160, 4)}px` }}
                        />
                      );
                    })}
                  </div>
                  <span className="text-xs text-gray-500 rotate-0 whitespace-nowrap">{point.period}</span>
                </div>
              ))}
            </div>
            <p className="mt-2 text-xs text-gray-400">
              Counts of entries into each stage that month, taken from the status history.
            </p>
          </section>

          {/* Breakdowns */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {([['Top companies', data!.companies], ['Top roles', data!.roles]] as const).map(([title, rows]) => (
              <section key={title} className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
                <div className="flex items-center gap-3 px-6 py-4 border-b border-gray-200">
                  <div className="p-2 rounded-lg bg-gray-100">
                    <Building2 className="w-5 h-5 text-gray-600" />
                  </div>
                  <h2 className="font-semibold text-gray-900">{title}</h2>
                </div>
                {rows.length === 0 ? (
                  <p className="px-6 py-8 text-sm text-gray-400 text-center">Nothing to show yet.</p>
                ) : (
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-left text-xs uppercase tracking-wide text-gray-500">
                        <th scope="col" className="px-6 py-2 font-semibold">{title === 'Top companies' ? 'Company' : 'Role'}</th>
                        <th scope="col" className="px-3 py-2 font-semibold text-right">Total</th>
                        <th scope="col" className="px-3 py-2 font-semibold text-right">Interviews</th>
                        <th scope="col" className="px-6 py-2 font-semibold text-right">Offers</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {rows.map((row) => (
                        <tr key={row.label} className="hover:bg-gray-50">
                          <td className="px-6 py-3 text-gray-900 truncate max-w-[16rem]">{row.label}</td>
                          <td className="px-3 py-3 text-right text-gray-600 tabular-nums">{row.total}</td>
                          <td className="px-3 py-3 text-right text-gray-600 tabular-nums">{row.interviewing}</td>
                          <td className="px-6 py-3 text-right text-gray-600 tabular-nums">{row.offers}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </section>
            ))}
          </div>

          {/* Reminders */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <section className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
              <div className="flex items-center gap-3 px-6 py-4 border-b border-gray-200">
                <div className="p-2 rounded-lg bg-amber-100">
                  <CalendarClock className="w-5 h-5 text-amber-600" />
                </div>
                <h2 className="font-semibold text-gray-900">Upcoming deadlines</h2>
              </div>
              {data!.upcomingDeadlines.length === 0 ? (
                <p className="px-6 py-8 text-sm text-gray-400 text-center">
                  No deadlines in the next 60 days.
                </p>
              ) : (
                <ul className="divide-y divide-gray-100">
                  {data!.upcomingDeadlines.map((item) => (
                    <li key={item.jobId} className="px-6 py-3 flex items-center justify-between gap-4">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-gray-900 truncate">{item.companyName}</p>
                        <p className="text-xs text-gray-500 truncate">{item.jobTitle}</p>
                      </div>
                      <span className="text-sm text-amber-700 font-medium flex-shrink-0">{item.deadline}</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
              <div className="flex items-center gap-3 px-6 py-4 border-b border-gray-200">
                <div className="p-2 rounded-lg bg-purple-100">
                  <Users className="w-5 h-5 text-purple-600" />
                </div>
                <h2 className="font-semibold text-gray-900">Active interviews</h2>
              </div>
              {data!.activeInterviews.length === 0 ? (
                <p className="px-6 py-8 text-sm text-gray-400 text-center">
                  Nothing in screening or interview right now.
                </p>
              ) : (
                <ul className="divide-y divide-gray-100">
                  {data!.activeInterviews.map((item) => (
                    <li key={item.jobId} className="px-6 py-3 flex items-center justify-between gap-4">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-gray-900 truncate">{item.companyName}</p>
                        <p className="text-xs text-gray-500 truncate">{item.jobTitle}</p>
                      </div>
                      <StatusBadge status={item.status} />
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          {/* Outcome summary */}
          <section className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 rounded-lg bg-green-500">
                <Trophy className="w-5 h-5 text-white" />
              </div>
              <h2 className="font-semibold text-gray-900">Outcomes</h2>
            </div>
            <dl className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4 text-sm">
              {[
                ['Total tracked', metrics!.total],
                ['Applications', metrics!.applied],
                ['Interviewing', metrics!.interviewing],
                ['Offers', metrics!.offers],
                ['Rejected', metrics!.rejected],
                ['Wishlist', metrics!.total - metrics!.applied],
              ].map(([label, value]) => (
                <div key={String(label)}>
                  <dt className="text-gray-500">{label}</dt>
                  <dd className="text-xl font-semibold text-gray-900 tabular-nums">{value}</dd>
                </div>
              ))}
            </dl>
          </section>
        </>
      )}
    </div>
  );
}
