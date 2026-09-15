import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusBadge } from './StatusBadge';

describe('StatusBadge', () => {
  it('renders the human readable label', () => {
    render(<StatusBadge status="phone_screen" />);

    expect(screen.getByText('Phone Screen')).toBeInTheDocument();
  });

  it('applies the colour for the status', () => {
    render(<StatusBadge status="offer" />);

    expect(screen.getByText('Offer')).toHaveClass('bg-green-100');
  });

  it('accepts extra classes without dropping the status colour', () => {
    render(<StatusBadge status="rejected" className="ml-2" />);

    const badge = screen.getByText('Rejected');
    expect(badge).toHaveClass('bg-red-100');
    expect(badge).toHaveClass('ml-2');
  });
});
