export interface ApiResponse<T> {
  success:   boolean;
  message?:  string;
  data?:     T;
  errorCode?: string;
  timestamp: number;
}

export interface PageResponse<T> {
  content:       T[];
  page_number?:  number;
  page_size?:   number;
  page?:         number;
  size?:         number;
  totalElements: number;
  totalPages:    number;
  last:          boolean;
}

export interface AxiosApiError {
  response?: { data: ApiResponse<never>; status: number };
}

