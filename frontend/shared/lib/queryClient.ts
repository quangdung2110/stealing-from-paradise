import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

export { QueryClientProvider };

export const createQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 1000 * 60,
        refetchOnWindowFocus: false,
        retry: 1,
        refetchOnMount: false,
      },
      mutations: {
        retry: 0,
      },
    },
  });
