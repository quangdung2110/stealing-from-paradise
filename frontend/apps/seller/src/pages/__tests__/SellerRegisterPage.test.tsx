import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import SellerRegisterPage from '../SellerRegisterPage';

const registerSeller = vi.fn(() => Promise.resolve());
vi.mock('@shared/store/authStore', () => ({ useAuthStore: () => ({ registerSeller }) }));

function renderPage() {
  render(<MemoryRouter><SellerRegisterPage /></MemoryRouter>);
}
const fill = (ph: RegExp, value: string) => fireEvent.change(screen.getByPlaceholderText(ph), { target: { value } });
const submit = () => fireEvent.click(screen.getByRole('button', { name: /Đăng ký cửa hàng/i }));

beforeEach(() => vi.clearAllMocks());

describe('SellerRegisterPage (UC-IDENTITY-006)', () => {
  it('blocks submit when passwords do not match', () => {
    renderPage();
    fill(/cua_hang_toi/i, 'shop');
    fill(/email@example/i, 'a@b.com');
    fill(/Ít nhất 6 ký tự/i, 'abcdef');
    fill(/Nhập lại mật khẩu/i, 'zzzzzz');
    submit();
    expect(screen.getByText(/Mật khẩu xác nhận không khớp/i)).toBeInTheDocument();
    expect(registerSeller).not.toHaveBeenCalled();
  });

  it('blocks submit when password is too short', () => {
    renderPage();
    fill(/cua_hang_toi/i, 'shop');
    fill(/email@example/i, 'a@b.com');
    fill(/Ít nhất 6 ký tự/i, 'abc');
    fill(/Nhập lại mật khẩu/i, 'abc');
    submit();
    expect(screen.getByText(/ít nhất 6 ký tự/i)).toBeInTheDocument();
    expect(registerSeller).not.toHaveBeenCalled();
  });

  it('registers a seller on valid input', async () => {
    renderPage();
    fill(/cua_hang_toi/i, 'shop');
    fill(/email@example/i, 'a@b.com');
    fill(/Ít nhất 6 ký tự/i, 'abcdef');
    fill(/Nhập lại mật khẩu/i, 'abcdef');
    submit();
    await waitFor(() => expect(registerSeller).toHaveBeenCalledWith({
      username: 'shop', email: 'a@b.com', password: 'abcdef',
    }));
  });
});
