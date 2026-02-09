/**
 * Accordion Showcase
 */
import { useState } from 'react'
import { Accordion, AccordionItem, AccordionTrigger, AccordionContent } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function AccordionShowcase() {
  const [type, setType] = useState<'single' | 'multiple'>('single')

  return (
    <div className="showcase">
      <PageHeader
        title="Accordion"
        description="접을 수 있는 콘텐츠 패널입니다. FAQ, 설정 등에 사용됩니다."
        stability="stable"
      />

      <LivePreview>
        <Accordion type={type} defaultValue="item-1">
          <AccordionItem value="item-1">
            <AccordionTrigger value="item-1">What is a Design System?</AccordionTrigger>
            <AccordionContent value="item-1">
              A design system is a collection of reusable components and guidelines that help teams build consistent user interfaces.
            </AccordionContent>
          </AccordionItem>
          <AccordionItem value="item-2">
            <AccordionTrigger value="item-2">Why use components?</AccordionTrigger>
            <AccordionContent value="item-2">
              Components allow you to break down complex UIs into smaller, reusable pieces. This makes development faster and maintenance easier.
            </AccordionContent>
          </AccordionItem>
          <AccordionItem value="item-3">
            <AccordionTrigger value="item-3">How to get started?</AccordionTrigger>
            <AccordionContent value="item-3">
              Import the component from @/shared/ui and use it in your React components. Check the code examples below for usage patterns.
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      </LivePreview>

      <section style={{ marginTop: '1.5rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Type Selector</h3>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          {(['single', 'multiple'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setType(t)}
              style={{
                padding: '0.5rem 1rem',
                background: type === t ? 'var(--accent-cyan)' : 'var(--bg-tertiary)',
                border: 'none',
                borderRadius: '6px',
                color: type === t ? '#000' : 'var(--text-secondary)',
                cursor: 'pointer',
                fontSize: '0.8125rem',
                fontWeight: 500,
              }}
            >
              {t}
            </button>
          ))}
        </div>
        <p style={{ marginTop: '0.5rem', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
          {type === 'single' ? 'Only one item can be open at a time' : 'Multiple items can be open simultaneously'}
        </p>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`<Accordion type="${type}" defaultValue="item-1">
  <AccordionItem value="item-1">
    <AccordionTrigger value="item-1">Question 1</AccordionTrigger>
    <AccordionContent value="item-1">
      Answer 1
    </AccordionContent>
  </AccordionItem>
  <AccordionItem value="item-2">
    <AccordionTrigger value="item-2">Question 2</AccordionTrigger>
    <AccordionContent value="item-2">
      Answer 2
    </AccordionContent>
  </AccordionItem>
</Accordion>`}
        </pre>
      </section>
    </div>
  )
}
