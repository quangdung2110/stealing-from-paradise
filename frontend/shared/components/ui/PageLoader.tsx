import Spinner from './Spinner';

/** Full-section loading indicator — used as the Suspense fallback. */
export default function PageLoader() {
  return (
    <div className="flex items-center justify-center py-24 text-blue-600">
      <Spinner className="w-8 h-8" />
    </div>
  );
}
