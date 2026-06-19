interface StripeSummary {
  totalSellers: number;
  completedSellers: number;
  pendingSellers: number;
  inProgressSellers: number;
  suspendedSellers: number;
}

interface StripeStatsCardsProps {
  summary: StripeSummary;
}

export default function StripeStatsCards({ summary }: StripeStatsCardsProps) {
  const cards = [
    {
      title: 'Tổng nhà bán hàng',
      val: summary.totalSellers,
      desc: 'Đã tạo tài khoản',
      gradient: 'from-slate-50 to-slate-100 border-slate-200 text-slate-800',
      dot: 'bg-slate-400',
    },
    {
      title: 'Hoàn thành KYC',
      val: summary.completedSellers,
      desc: 'Được phép bán hàng',
      gradient: 'from-emerald-50 to-emerald-100/50 border-emerald-200 text-emerald-800',
      dot: 'bg-emerald-500',
    },
    {
      title: 'Đang xác minh',
      val: summary.inProgressSellers,
      desc: 'Đang điền thông tin',
      gradient: 'from-sky-50 to-sky-100/50 border-sky-200 text-sky-800',
      dot: 'bg-sky-500',
    },
    {
      title: 'Chưa bắt đầu',
      val: summary.pendingSellers,
      desc: 'Cần bắt đầu liên kết',
      gradient: 'from-amber-50 to-amber-100/50 border-amber-200 text-amber-800',
      dot: 'bg-amber-500',
    },
    {
      title: 'Bị hạn chế / Khóa',
      val: summary.suspendedSellers,
      desc: 'Lỗi xác minh Stripe',
      gradient: 'from-rose-50 to-rose-100/50 border-rose-200 text-rose-800',
      dot: 'bg-rose-500',
    },
  ];

  return (
    <div className="grid grid-cols-2 lg:grid-cols-5 gap-5 mb-8">
      {cards.map((card) => (
        <div
          key={card.title}
          className={`rounded-2xl border p-5 bg-gradient-to-br ${card.gradient} transition-transform hover:-translate-y-0.5 duration-200 shadow-sm`}
        >
          <div className="flex items-center gap-1.5 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
            <span className={`w-2.5 h-2.5 rounded-full ${card.dot} inline-block animate-pulse`} />
            {card.title}
          </div>
          <div className="text-3xl font-black text-gray-900 tracking-tight">{card.val}</div>
          <p className="text-xs text-gray-500 mt-1">{card.desc}</p>
        </div>
      ))}
    </div>
  );
}
