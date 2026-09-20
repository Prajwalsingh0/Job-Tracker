import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDialog } from './ConfirmDialog';
import { ToastProvider, useToast } from './Toast';

describe('ConfirmDialog', () => {
  it('renders nothing while closed', () => {
    render(
      <ConfirmDialog
        isOpen={false}
        title="Delete?"
        message="Sure?"
        onConfirm={() => {}}
        onCancel={() => {}}
      />,
    );

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('announces itself as a modal and wires up both actions', async () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    const user = userEvent.setup();

    render(
      <ConfirmDialog
        isOpen
        title="Delete this job?"
        message="This cannot be undone."
        confirmLabel="Delete"
        destructive
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    );

    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleName('Delete this job?');
    expect(screen.getByText('This cannot be undone.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Delete' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('cancels on Escape', async () => {
    const onCancel = vi.fn();
    const user = userEvent.setup();

    render(
      <ConfirmDialog isOpen title="Title" message="Message" onConfirm={() => {}} onCancel={onCancel} />,
    );

    await user.keyboard('{Escape}');
    expect(onCancel).toHaveBeenCalled();
  });
});

function ToastTrigger() {
  const { showToast } = useToast();
  return <button onClick={() => showToast('Resume uploaded', 'success')}>trigger</button>;
}

describe('Toast', () => {
  it('shows the message in a live region and can be dismissed', async () => {
    const user = userEvent.setup();

    render(
      <ToastProvider>
        <ToastTrigger />
      </ToastProvider>,
    );

    await user.click(screen.getByRole('button', { name: 'trigger' }));
    expect(screen.getByText('Resume uploaded')).toBeInTheDocument();
    // Announced politely, so screen readers read it without stealing focus.
    expect(screen.getByText('Resume uploaded').closest('[aria-live]')).toHaveAttribute(
      'aria-live',
      'polite',
    );

    await user.click(screen.getByRole('button', { name: 'Dismiss notification' }));
    expect(screen.queryByText('Resume uploaded')).not.toBeInTheDocument();
  });
});
