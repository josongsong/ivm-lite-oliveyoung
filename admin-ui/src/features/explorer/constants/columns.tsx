/**
 * Explorer 테이블 컬럼 정의 (OCP 준수)
 * - 컬럼 추가/수정 시 이 파일만 수정
 * - 재사용 가능한 셀 렌더러
 */
import { Database, Eye, Layers } from 'lucide-react'
import { createColumnHelper } from '@tanstack/react-table'
import type { RawDataListEntry, SliceListItem, ViewDefinitionEntry } from '@/shared/types'
import { ActionCell, DateCell, EntityCell, VersionCell } from './cellRenderers'

// RawData 컬럼
const rawDataColumnHelper = createColumnHelper<RawDataListEntry>()

export const rawDataColumns = [
  rawDataColumnHelper.accessor('entityId', {
    header: 'Entity ID',
    cell: (info) => <EntityCell value={info.getValue()} icon={Database} />,
  }),
  rawDataColumnHelper.accessor('version', {
    header: 'Version',
    cell: (info) => <VersionCell value={info.getValue()} />,
  }),
  rawDataColumnHelper.accessor('schemaRef', {
    header: 'Schema',
    cell: (info) => (
      <span className="schema-text">{info.getValue()?.split('/').pop() || '-'}</span>
    ),
  }),
  rawDataColumnHelper.accessor('updatedAt', {
    header: 'Updated',
    cell: (info) => <DateCell value={info.getValue()} />,
  }),
  rawDataColumnHelper.display({
    id: 'action',
    header: '',
    cell: ActionCell,
  }),
]

// Slice 컬럼
const sliceColumnHelper = createColumnHelper<SliceListItem>()

export const sliceColumns = [
  sliceColumnHelper.accessor('entityId', {
    header: 'Entity ID',
    cell: (info) => <EntityCell value={info.getValue()} icon={Layers} />,
  }),
  sliceColumnHelper.accessor('version', {
    header: 'Version',
    cell: (info) => <VersionCell value={info.getValue()} />,
  }),
  sliceColumnHelper.accessor('ruleSetId', {
    header: 'RuleSet',
    cell: (info) => <span className="schema-text">{info.getValue() || '-'}</span>,
  }),
  sliceColumnHelper.accessor('updatedAt', {
    header: 'Updated',
    cell: (info) => <DateCell value={info.getValue()} />,
  }),
  sliceColumnHelper.display({
    id: 'action',
    header: '',
    cell: ActionCell,
  }),
]

// View 컬럼
const viewColumnHelper = createColumnHelper<ViewDefinitionEntry>()

export const viewColumns = [
  viewColumnHelper.accessor('id', {
    header: 'View Definition ID',
    cell: (info) => <EntityCell value={info.getValue()} icon={Eye} />,
  }),
  viewColumnHelper.accessor('version', {
    header: 'Version',
    cell: (info) => <VersionCell value={info.getValue()} />,
  }),
  viewColumnHelper.accessor('requiredSlices', {
    header: 'Required Slices',
    cell: (info) => <span className="schema-text">{info.getValue()?.join(', ') || '-'}</span>,
  }),
  viewColumnHelper.accessor('status', {
    header: 'Status',
    cell: (info) => (
      <span className={`status-badge ${info.getValue()?.toLowerCase()}`}>{info.getValue()}</span>
    ),
  }),
  viewColumnHelper.display({
    id: 'action',
    header: '',
    cell: ActionCell,
  }),
]
