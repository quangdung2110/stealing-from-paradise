import { type AdminSellerStripeAccountItem } from '@shared/api/admin.api';
import { fmtDateTime } from '@shared/utils/format';

const STATUS_CONFIG: Record<string, { label: string; badgeClass: string; icon: string }> = {
  COMPLETE: {
    label: 'Hoàn thành',
    badgeClass: 'bg-emerald-100 text-emerald-800 border-emerald-200',
    icon: '✅',
  },
  IN_PROGRESS: {
    label: 'Đang KYC',
    badgeClass: 'bg-sky-100 text-sky-800 border-sky-200',
    icon: '⏳',
  },
  PENDING: {
    label: 'Chưa bắt đầu',
    badgeClass: 'bg-amber-100 text-amber-800 border-amber-200',
    icon: '🔒',
  },
  SUSPENDED: {
    label: 'Bị hạn chế',
    badgeClass: 'bg-rose-100 text-rose-800 border-rose-200',
    icon: '⚠️',
  },
};

interface StripeAccountsTableProps {
  filteredAccounts: AdminSellerStripeAccountItem[];
  accounts: AdminSellerStripeAccountItem[];
}

function capabilityCount(acc: AdminSellerStripeAccountItem) {
  return [acc.detailsSubmitted, acc.chargesEnabled, acc.payoutsEnabled].filter(Boolean).length;
}

function nextAction(acc: AdminSellerStripeAccountItem) {
  if (acc.onboardingStatus === 'SUSPENDED') return 'Kiểm tra restriction trong Stripe';
  if (!acc.detailsSubmitted) return 'Nhắc seller hoàn tất KYC';
  if (!acc.chargesEnabled) return 'Chờ Stripe bật charges';
  if (!acc.payoutsEnabled) return 'Chờ Stripe bật payouts';
  return 'Tài khoản sẵn sàng nhận thanh toán';
}

function CapabilityPill({ label, enabled }: { label: string; enabled: boolean }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-semibold ${
        enabled
          ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
          : 'border-rose-200 bg-rose-50 text-rose-700'
      }`}
    >
      <span>{enabled ? '✓' : '!'}</span>
      {label}
    </span>
  );
}

export default function StripeAccountsTable({
  filteredAccounts,
  accounts,
}: StripeAccountsTableProps) {
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse text-sm">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-200 text-xs font-semibold text-gray-500 uppercase tracking-wider">
              <th className="py-4 px-6">Seller</th>
              <th className="py-4 px-6">Stripe Account</th>
              <th className="py-4 px-6">Onboarding</th>
              <th className="py-4 px-6">Capabilities</th>
              <th className="py-4 px-6">Next action</th>
              <th className="py-4 px-6">Updated</th>
              <th className="py-4 px-6 text-right">Console</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 text-gray-700">
            {filteredAccounts.map((acc) => {
              const cfg = STATUS_CONFIG[acc.onboardingStatus] || STATUS_CONFIG.PENDING;
              const readyCount = capabilityCount(acc);
              const progressPct = Math.round((readyCount / 3) * 100);
              const dashboardUrl =
                acc.expressDashboardUrl ||
                `https://dashboard.stripe.com/connect/accounts/${acc.stripeAccountId}`;

              return (
                <tr key={acc.sellerId} className="hover:bg-slate-50/70 transition-colors">
                  <td className="py-4 px-6 align-top">
                    <p className="font-bold text-gray-900">#{acc.sellerId}</p>
                    <span className="mt-1 inline-flex rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-semibold uppercase text-gray-600">
                      {acc.accountStatus || 'PENDING'}
                    </span>
                  </td>
                  <td className="py-4 px-6 align-top">
                    <p className="font-mono text-xs text-gray-700 break-all">{acc.stripeAccountId}</p>
                    <p className="mt-1 text-[11px] text-gray-400">Created {fmtDateTime(acc.createdAt)}</p>
                  </td>
                  <td className="py-4 px-6 align-top min-w-52">
                    <span
                      className={`inline-flex items-center gap-1 px-3 py-1 border rounded-full text-xs font-semibold shadow-sm ${cfg.badgeClass}`}
                    >
                      <span>{cfg.icon}</span>
                      {cfg.label}
                    </span>
                    <div className="mt-3 h-2 w-40 rounded-full bg-gray-100 overflow-hidden">
                      <div
                        className={`h-full rounded-full ${readyCount === 3 ? 'bg-emerald-500' : 'bg-sky-500'}`}
                        style={{ width: `${progressPct}%` }}
                      />
                    </div>
                    <p className="mt-1 text-[11px] text-gray-400">{readyCount}/3 checks ready</p>
                  </td>
                  <td className="py-4 px-6 align-top">
                    <div className="flex max-w-xs flex-wrap gap-1.5">
                      <CapabilityPill label="KYC" enabled={acc.detailsSubmitted} />
                      <CapabilityPill label="Charges" enabled={acc.chargesEnabled} />
                      <CapabilityPill label="Payouts" enabled={acc.payoutsEnabled} />
                    </div>
                  </td>
                  <td className="py-4 px-6 align-top">
                    <p className="max-w-xs text-xs font-medium text-gray-700">{nextAction(acc)}</p>
                  </td>
                  <td className="py-4 px-6 align-top text-xs text-gray-400 whitespace-nowrap">
                    {fmtDateTime(acc.updatedAt)}
                  </td>
                  <td className="py-4 px-6 align-top text-right">
                    <a
                      href={dashboardUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-semibold text-xs rounded-lg transition-colors border border-indigo-100"
                    >
                      Open Stripe ↗
                    </a>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="bg-gray-50 px-6 py-4 border-t border-gray-200 text-xs text-gray-500 font-medium flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <span>
          Hiển thị <strong>{filteredAccounts.length}</strong> / <strong>{accounts.length}</strong> nhà bán hàng.
        </span>
        <span className="italic">
          KYC, charges và payouts đều phải sẵn sàng trước khi seller nhận tiền ổn định.
        </span>
      </div>
    </div>
  );
}
