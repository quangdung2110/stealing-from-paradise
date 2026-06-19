import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import MarkdownRenderer, { parseInline } from '../MarkdownRenderer';

// Helper to render component wrapped in Router
const renderWithRouter = (ui: React.ReactElement) => {
  return render(<BrowserRouter>{ui}</BrowserRouter>);
};

describe('MarkdownRenderer Inline Parsing', () => {
  it('parses bold text correctly', () => {
    renderWithRouter(<>{parseInline('Đây là **chữ đậm** trong văn bản')}</>);
    const boldEl = screen.getByText('chữ đậm');
    expect(boldEl.tagName).toBe('STRONG');
    expect(boldEl).toHaveClass('font-bold');
  });

  it('parses italic text correctly', () => {
    renderWithRouter(<>{parseInline('Đây là *chữ nghiêng* trong văn bản')}</>);
    const italicEl = screen.getByText('chữ nghiêng');
    expect(italicEl.tagName).toBe('EM');
    expect(italicEl).toHaveClass('italic');
  });

  it('parses order codes as clickable router Links', () => {
    renderWithRouter(<>{parseInline('Đơn hàng của bạn là FE-ORD-PAID-900102.')}</>);
    const linkEl = screen.getByRole('link', { name: 'FE-ORD-PAID-900102' });
    expect(linkEl).toBeInTheDocument();
    expect(linkEl.getAttribute('href')).toBe('/orders/900102');
  });

  it('parses alternate order codes and extracts last numerical group', () => {
    renderWithRouter(<>{parseInline('Đơn OR-20260612-900118 đã hủy.')}</>);
    const linkEl = screen.getByRole('link', { name: 'OR-20260612-900118' });
    expect(linkEl).toBeInTheDocument();
    expect(linkEl.getAttribute('href')).toBe('/orders/900118');
  });

  it('handles order codes inside bold text recursively', () => {
    renderWithRouter(<>{parseInline('Hãy kiểm tra **FE-ORD-PAID-900102** ngay.')}</>);
    const boldEl = screen.getByText('FE-ORD-PAID-900102').closest('strong');
    expect(boldEl).toBeInTheDocument();
    const linkEl = screen.getByRole('link', { name: 'FE-ORD-PAID-900102' });
    expect(linkEl).toBeInTheDocument();
    expect(linkEl.getAttribute('href')).toBe('/orders/900102');
  });
});

describe('MarkdownRenderer Block Parsing', () => {
  it('renders paragraphs with line breaks', () => {
    const text = 'Dòng thứ nhất\nDòng thứ hai';
    renderWithRouter(<MarkdownRenderer text={text} />);
    expect(screen.getByText(/Dòng thứ nhất/)).toBeInTheDocument();
    expect(screen.getByText(/Dòng thứ hai/)).toBeInTheDocument();
  });

  it('renders unordered lists correctly', () => {
    const text = '- Mục thứ nhất\n- Mục thứ hai';
    renderWithRouter(<MarkdownRenderer text={text} />);
    const listItems = screen.getAllByRole('listitem');
    expect(listItems).toHaveLength(2);
    expect(listItems[0]).toHaveTextContent('Mục thứ nhất');
    expect(listItems[1]).toHaveTextContent('Mục thứ hai');
  });

  it('renders markdown tables correctly', () => {
    const tableText = 
      '| Cột 1 | Cột 2 |\n' +
      '|---|---|\n' +
      '| Dữ liệu 1 | Dữ liệu 2 |';
    renderWithRouter(<MarkdownRenderer text={tableText} />);
    
    // Check table headers
    expect(screen.getByText('Cột 1')).toBeInTheDocument();
    expect(screen.getByText('Cột 2')).toBeInTheDocument();
    
    // Check table body cells
    expect(screen.getByText('Dữ liệu 1')).toBeInTheDocument();
    expect(screen.getByText('Dữ liệu 2')).toBeInTheDocument();
  });
});
