import React from 'react';
import { Link } from 'react-router-dom';

/**
 * Parses inline formatting tags:
 * - Bold: **text**
 * - Italics: *text*
 * - Order codes: e.g. FE-ORD-PAID-900102, OR-20260612-900118, etc.
 * Supports recursive parsing (e.g. order code inside a bold block).
 */
export function parseInline(text: string): React.ReactNode[] {
  if (!text) return [];

  // Match bold (**text**), italics (*text*), and common order codes
  const regex = /(\*\*.*?\*\*|\*.*?\*|#?(?:FE-ORD-PAID-|FE-ORD-|OR-)[0-9-]+)/gi;
  const parts = text.split(regex);

  return parts.map((part, index) => {
    // 1. Bold text
    if (part.startsWith('**') && part.endsWith('**')) {
      return (
        <strong key={index} className="font-bold text-gray-950">
          {parseInline(part.slice(2, -2))}
        </strong>
      );
    }
    // 2. Italic text
    if (part.startsWith('*') && part.endsWith('*')) {
      return (
        <em key={index} className="italic text-gray-700">
          {parseInline(part.slice(1, -1))}
        </em>
      );
    }
    // 3. Order code link
    const orderCodeRegex = /^#?(?:FE-ORD-PAID-|FE-ORD-|OR-)[0-9-]+$/i;
    if (orderCodeRegex.test(part)) {
      const match = part.match(/(\d+)$/);
      const orderId = match ? match[1] : null;
      if (orderId) {
        return (
          <Link
            key={index}
            to={`/orders/${orderId}`}
            className="text-blue-600 hover:text-blue-800 font-semibold underline underline-offset-2 transition-colors inline-flex items-center gap-0.5"
            title="Xem chi tiết đơn hàng"
            onClick={(e) => {
              // Ensure clicking the link navigates to the order details page.
              e.stopPropagation();
            }}
          >
            {part}
          </Link>
        );
      }
    }
    // 4. Default plain text
    return part;
  });
}

interface MarkdownRendererProps {
  text: string;
}

/**
 * Parses block-level markdown elements (paragraphs, tables, bullet points)
 * and outputs them as a styled, clean React layout.
 */
export default function MarkdownRenderer({ text }: MarkdownRendererProps) {
  if (!text) return null;

  const lines = text.split('\n');
  const blocks: React.ReactNode[] = [];

  let currentListItems: React.ReactNode[] = [];
  let currentTableRows: string[][] = [];
  let currentParagraphLines: string[] = [];

  let blockKey = 0;

  const flushParagraph = () => {
    if (currentParagraphLines.length > 0) {
      blocks.push(
        <p key={`p-${blockKey++}`} className="mb-2 leading-relaxed text-gray-800 last:mb-0">
          {currentParagraphLines.map((line, lineIdx) => (
            <React.Fragment key={lineIdx}>
              {lineIdx > 0 && <br />}
              {parseInline(line)}
            </React.Fragment>
          ))}
        </p>
      );
      currentParagraphLines = [];
    }
  };

  const flushList = () => {
    if (currentListItems.length > 0) {
      blocks.push(
        <ul key={`ul-${blockKey++}`} className="list-disc pl-5 mb-3 space-y-1 text-gray-800">
          {currentListItems}
        </ul>
      );
      currentListItems = [];
    }
  };

  const flushTable = () => {
    if (currentTableRows.length > 0) {
      // Filter out separator lines (e.g. |---|---|)
      const validRows = currentTableRows.filter(row => {
        return !row.every(cell => /^[: -]+$/.test(cell.trim()));
      });

      if (validRows.length > 0) {
        // Check if there was a separator line in the source at index 1
        const hasHeader = currentTableRows.length > 1 &&
          currentTableRows[1].every(cell => /^[: -]+$/.test(cell.trim()));

        const headers = hasHeader ? validRows[0] : [];
        const bodyRows = hasHeader ? validRows.slice(1) : validRows;

        blocks.push(
          <div key={`table-wrapper-${blockKey++}`} className="overflow-x-auto my-3 border border-gray-200 rounded-xl shadow-sm max-w-full">
            <table className="min-w-full divide-y divide-gray-200 text-xs text-left">
              {hasHeader && (
                <thead className="bg-gray-50/80 text-gray-700 font-semibold uppercase tracking-wider backdrop-blur-sm">
                  <tr>
                    {headers.map((cell, cellIdx) => (
                      <th key={cellIdx} className="px-3 py-2 border-b border-gray-200 font-bold">
                        {parseInline(cell.trim())}
                      </th>
                    ))}
                  </tr>
                </thead>
              )}
              <tbody className="bg-white divide-y divide-gray-100 text-gray-600">
                {bodyRows.map((row, rowIdx) => (
                  <tr key={rowIdx} className="hover:bg-gray-50/50 transition-colors">
                    {row.map((cell, cellIdx) => (
                      <td key={cellIdx} className="px-3 py-2 align-middle border-b border-gray-100 last:border-b-0">
                        {parseInline(cell.trim())}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      }
      currentTableRows = [];
    }
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    // 1. Table Row Detection (starts and ends with '|')
    if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
      flushParagraph();
      flushList();
      const cells = line.split('|').slice(1, -1);
      currentTableRows.push(cells);
      continue;
    }

    // 2. Unordered List Item Detection (starts with '-' or '*')
    const listMatch = line.match(/^(\s*)[-*]\s+(.*)$/);
    if (listMatch) {
      flushParagraph();
      flushTable();
      const content = listMatch[2];
      currentListItems.push(
        <li key={`li-${blockKey}-${currentListItems.length}`} className="text-sm">
          {parseInline(content)}
        </li>
      );
      continue;
    }

    // 3. Empty Line
    if (trimmed === '') {
      flushParagraph();
      flushList();
      flushTable();
      continue;
    }

    // 4. Normal text line (accumulate paragraph)
    flushList();
    flushTable();
    currentParagraphLines.push(line);
  }

  // Final flush
  flushParagraph();
  flushList();
  flushTable();

  return <div className="markdown-body space-y-1">{blocks}</div>;
}
