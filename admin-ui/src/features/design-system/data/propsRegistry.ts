/**
 * Props Registry - Component Props Metadata
 * 컴포넌트별 Props 메타데이터 정적 정의
 */

import type { ExtractedProp } from './types'

/**
 * 컴포넌트별 Props 메타데이터
 */
export const COMPONENT_PROPS_REGISTRY: Record<string, ExtractedProp[]> = {
  Button: [
    { name: 'variant', type: "'primary' | 'secondary' | 'outline' | 'ghost' | 'danger' | 'success'", required: false, defaultValue: "'secondary'", description: 'Visual variant' },
    { name: 'size', type: "'sm' | 'md' | 'lg'", required: false, defaultValue: "'md'", description: 'Size variant' },
    { name: 'loading', type: 'boolean', required: false, defaultValue: 'false', description: 'Shows loading spinner and disables button' },
    { name: 'icon', type: 'ReactNode', required: false, description: 'Icon element (ReactNode)' },
    { name: 'iconPosition', type: "'left' | 'right'", required: false, defaultValue: "'left'", description: 'Icon position relative to children' },
    { name: 'fullWidth', type: 'boolean', required: false, defaultValue: 'false', description: 'Makes button full width' },
    { name: 'disabled', type: 'boolean', required: false, defaultValue: 'false', description: 'Disables the button' },
    { name: 'children', type: 'ReactNode', required: false, description: 'Button content' },
    { name: 'onClick', type: '(event: MouseEvent<HTMLButtonElement>) => void', required: false, description: 'Click event handler' },
  ],

  IconButton: [
    { name: 'icon', type: 'ReactNode', required: true, description: 'Icon element to display' },
    { name: 'variant', type: "'default' | 'ghost' | 'danger'", required: false, defaultValue: "'default'", description: 'Visual variant' },
    { name: 'size', type: "'sm' | 'md' | 'lg'", required: false, defaultValue: "'md'", description: 'Button size' },
    { name: 'aria-label', type: 'string', required: true, description: 'Accessible label for the button' },
    { name: 'disabled', type: 'boolean', required: false, defaultValue: 'false', description: 'Disables the button' },
  ],

  Input: [
    { name: 'size', type: "'sm' | 'md' | 'lg'", required: false, defaultValue: "'md'", description: 'Input size' },
    { name: 'error', type: 'boolean', required: false, defaultValue: 'false', description: 'Shows error state' },
    { name: 'errorMessage', type: 'string', required: false, description: 'Error message to display' },
    { name: 'helperText', type: 'string', required: false, description: 'Helper text below input' },
    { name: 'leftIcon', type: 'ReactNode', required: false, description: 'Icon on the left side' },
    { name: 'rightIcon', type: 'ReactNode', required: false, description: 'Icon on the right side' },
    { name: 'placeholder', type: 'string', required: false, description: 'Placeholder text' },
    { name: 'disabled', type: 'boolean', required: false, defaultValue: 'false', description: 'Disables the input' },
    { name: 'value', type: 'string', required: false, description: 'Controlled value' },
    { name: 'onChange', type: '(event: ChangeEvent<HTMLInputElement>) => void', required: false, description: 'Change event handler' },
  ],

  Select: [
    { name: 'options', type: 'Array<{ value: string; label: string; disabled?: boolean }>', required: true, description: 'Options to display' },
    { name: 'value', type: 'string', required: false, description: 'Selected value' },
    { name: 'onChange', type: '(value: string) => void', required: false, description: 'Change handler' },
    { name: 'placeholder', type: 'string', required: false, defaultValue: "'Select...'", description: 'Placeholder text' },
    { name: 'size', type: "'sm' | 'md' | 'lg'", required: false, defaultValue: "'md'", description: 'Select size' },
    { name: 'disabled', type: 'boolean', required: false, defaultValue: 'false', description: 'Disables the select' },
    { name: 'icon', type: 'ReactNode', required: false, description: 'Leading icon' },
  ],

  TextArea: [
    { name: 'size', type: "'sm' | 'md' | 'lg'", required: false, defaultValue: "'md'", description: 'TextArea size' },
    { name: 'error', type: 'boolean', required: false, defaultValue: 'false', description: 'Shows error state' },
    { name: 'errorMessage', type: 'string', required: false, description: 'Error message to display' },
    { name: 'rows', type: 'number', required: false, defaultValue: '3', description: 'Number of visible rows' },
    { name: 'resize', type: "'none' | 'vertical' | 'horizontal' | 'both'", required: false, defaultValue: "'vertical'", description: 'Resize behavior' },
    { name: 'disabled', type: 'boolean', required: false, defaultValue: 'false', description: 'Disables the textarea' },
  ],

  Modal: [
    { name: 'isOpen', type: 'boolean', required: true, description: 'Controls modal visibility' },
    { name: 'onClose', type: '() => void', required: true, description: 'Close handler' },
    { name: 'title', type: 'string', required: false, description: 'Modal title' },
    { name: 'size', type: "'sm' | 'md' | 'lg' | 'xl'", required: false, defaultValue: "'md'", description: 'Modal size' },
    { name: 'children', type: 'ReactNode', required: true, description: 'Modal content' },
    { name: 'closeOnOverlayClick', type: 'boolean', required: false, defaultValue: 'true', description: 'Close when clicking overlay' },
  ],

  Tabs: [
    { name: 'tabs', type: 'Array<{ id: string; label: string; content?: ReactNode }>', required: true, description: 'Tab configuration' },
    { name: 'activeTab', type: 'string', required: false, description: 'Active tab ID (controlled)' },
    { name: 'defaultTab', type: 'string', required: false, description: 'Default active tab ID' },
    { name: 'onChange', type: '(tabId: string) => void', required: false, description: 'Tab change handler' },
    { name: 'variant', type: "'line' | 'pill' | 'boxed'", required: false, defaultValue: "'line'", description: 'Visual variant' },
  ],

  Table: [
    { name: 'columns', type: 'Array<Column<T>>', required: true, description: 'Column definitions' },
    { name: 'data', type: 'T[]', required: true, description: 'Data to display' },
    { name: 'loading', type: 'boolean', required: false, defaultValue: 'false', description: 'Shows loading state' },
    { name: 'emptyMessage', type: 'string', required: false, defaultValue: "'No data'", description: 'Message when data is empty' },
    { name: 'striped', type: 'boolean', required: false, defaultValue: 'false', description: 'Alternating row colors' },
  ],

  StatusBadge: [
    { name: 'status', type: "'success' | 'warning' | 'error' | 'info' | 'neutral'", required: true, description: 'Badge status' },
    { name: 'children', type: 'ReactNode', required: true, description: 'Badge content' },
    { name: 'size', type: "'sm' | 'md'", required: false, defaultValue: "'md'", description: 'Badge size' },
  ],

  Loading: [
    { name: 'size', type: "'sm' | 'md' | 'lg'", required: false, defaultValue: "'md'", description: 'Spinner size' },
    { name: 'text', type: 'string', required: false, description: 'Loading text' },
    { name: 'fullScreen', type: 'boolean', required: false, defaultValue: 'false', description: 'Centers in full screen' },
  ],

  Card: [
    { name: 'title', type: 'string', required: false, description: 'Card title' },
    { name: 'subtitle', type: 'string', required: false, description: 'Card subtitle' },
    { name: 'children', type: 'ReactNode', required: true, description: 'Card content' },
    { name: 'padding', type: "'none' | 'sm' | 'md' | 'lg'", required: false, defaultValue: "'md'", description: 'Content padding' },
    { name: 'shadow', type: "'none' | 'sm' | 'md' | 'lg'", required: false, defaultValue: "'sm'", description: 'Shadow depth' },
  ],

  Chip: [
    { name: 'children', type: 'ReactNode', required: true, description: 'Chip content' },
    { name: 'variant', type: "'default' | 'primary' | 'success' | 'warning' | 'error'", required: false, defaultValue: "'default'", description: 'Visual variant' },
    { name: 'size', type: "'sm' | 'md'", required: false, defaultValue: "'md'", description: 'Chip size' },
    { name: 'onDelete', type: '() => void', required: false, description: 'Delete handler (shows delete button)' },
  ],

  Accordion: [
    { name: 'items', type: 'Array<{ id: string; title: string; content: ReactNode }>', required: true, description: 'Accordion items' },
    { name: 'multiple', type: 'boolean', required: false, defaultValue: 'false', description: 'Allow multiple open items' },
    { name: 'defaultOpenIds', type: 'string[]', required: false, description: 'Initially open item IDs' },
  ],

  Pagination: [
    { name: 'currentPage', type: 'number', required: true, description: 'Current page number' },
    { name: 'totalPages', type: 'number', required: true, description: 'Total number of pages' },
    { name: 'onPageChange', type: '(page: number) => void', required: true, description: 'Page change handler' },
    { name: 'siblingCount', type: 'number', required: false, defaultValue: '1', description: 'Visible siblings on each side' },
  ],

  Tooltip: [
    { name: 'content', type: 'ReactNode', required: true, description: 'Tooltip content' },
    { name: 'children', type: 'ReactNode', required: true, description: 'Trigger element' },
    { name: 'position', type: "'top' | 'bottom' | 'left' | 'right'", required: false, defaultValue: "'top'", description: 'Tooltip position' },
    { name: 'delay', type: 'number', required: false, defaultValue: '200', description: 'Show delay in ms' },
  ],

  Label: [
    { name: 'htmlFor', type: 'string', required: false, description: 'Associated input ID' },
    { name: 'required', type: 'boolean', required: false, defaultValue: 'false', description: 'Shows required indicator' },
    { name: 'children', type: 'ReactNode', required: true, description: 'Label text' },
  ],
}
