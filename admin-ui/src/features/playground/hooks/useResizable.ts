import { useCallback, useEffect, useRef, useState } from 'react'

interface UseResizableOptions {
  initialLeftWidth?: number
  initialSampleHeight?: number
  minLeftWidth?: number
  maxLeftWidth?: number
  minSampleHeight?: number
  maxSampleHeight?: number
}

export function useResizable({
  initialLeftWidth = 50,
  initialSampleHeight = 250,
  minLeftWidth = 25,
  maxLeftWidth = 75,
  minSampleHeight = 100,
  maxSampleHeight = 500,
}: UseResizableOptions = {}) {
  const [leftPanelWidth, setLeftPanelWidth] = useState(initialLeftWidth)
  const [sampleHeight, setSampleHeight] = useState(initialSampleHeight)
  const [isResizingH, setIsResizingH] = useState(false)
  const [isResizingV, setIsResizingV] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const editorPanelRef = useRef<HTMLDivElement>(null)

  const handleHorizontalMouseDown = useCallback(() => {
    setIsResizingH(true)
  }, [])

  const handleVerticalMouseDown = useCallback(() => {
    setIsResizingV(true)
  }, [])

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (isResizingH && containerRef.current) {
        const rect = containerRef.current.getBoundingClientRect()
        const newWidth = ((e.clientX - rect.left) / rect.width) * 100
        setLeftPanelWidth(Math.min(Math.max(newWidth, minLeftWidth), maxLeftWidth))
      }
      if (isResizingV && editorPanelRef.current) {
        const rect = editorPanelRef.current.getBoundingClientRect()
        const newHeight = rect.bottom - e.clientY
        setSampleHeight(Math.min(Math.max(newHeight, minSampleHeight), maxSampleHeight))
      }
    }

    const handleMouseUp = () => {
      setIsResizingH(false)
      setIsResizingV(false)
    }

    if (isResizingH || isResizingV) {
      window.addEventListener('mousemove', handleMouseMove)
      window.addEventListener('mouseup', handleMouseUp)
    }

    return () => {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }
  }, [isResizingH, isResizingV, minLeftWidth, maxLeftWidth, minSampleHeight, maxSampleHeight])

  return {
    leftPanelWidth,
    sampleHeight,
    isResizingH,
    isResizingV,
    containerRef,
    editorPanelRef,
    handleHorizontalMouseDown,
    handleVerticalMouseDown,
  }
}
