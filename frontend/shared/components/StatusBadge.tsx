interface StatusConfig {
  bg: string;
  color: string;
  label: string;
}

interface StatusBadgeProps {
  status: string;
  config: Record<string, StatusConfig>;
  fallback?: StatusConfig;
}

export default function StatusBadge({ status, config, fallback }: StatusBadgeProps) {
  const c = config[status] ?? fallback ?? { bg: 'bg-gray-100', color: 'text-gray-600', label: status };
  return (
    <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${c.bg} ${c.color}`}>
      {c.label}
    </span>
  );
}
