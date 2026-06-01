import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Empty, Result, Skeleton, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import {
	buildDashboardChartData,
	dashboardKeys,
	getRealmMiniAppMetrics,
	type DashboardMetricPoint,
	type RealmMiniAppMetrics,
} from '@/entities/dashboard'
import { getAdminRealms, realmKeys } from '@/entities/realm'
import { Roles, useUserStore } from '@/entities/user'
import { DashboardMetricsGrid } from '@/features/dashboard-metrics'
import {
	CallsCountRing,
	LaunchesDeltaChart,
	SsoDeltaChart,
	TotalsOverviewChart,
} from '@/widgets/dashboard-charts'

import styles from './DashboardPage.module.css'

const { Text, Title } = Typography

const formatUpdatedAt = (value: number) =>
	value
		? new Date(value).toLocaleTimeString('ru-RU', {
				hour: '2-digit',
				minute: '2-digit',
				second: '2-digit',
			})
		: '—'

const getErrorStatus = (error: unknown) => {
	if (
		typeof error === 'object' &&
		error !== null &&
		'response' in error &&
		typeof (error as { response?: { status?: unknown } }).response?.status !==
			'undefined'
	) {
		const status = Number((error as { response?: { status?: unknown } }).response?.status)

		return Number.isNaN(status) ? undefined : status
	}

	if (typeof error !== 'object' || error === null || !('status' in error)) {
		return undefined
	}

	const status = Number((error as { status?: unknown }).status)

	return Number.isNaN(status) ? undefined : status
}

const parseWindowSeconds = (range: string) => {
	const value = range.trim().toLowerCase()

	if (value.endsWith('m')) {
		return Number(value.slice(0, -1)) * 60
	}

	if (value.endsWith('h')) {
		return Number(value.slice(0, -1)) * 3600
	}

	const seconds = Number(value)

	return Number.isFinite(seconds) && seconds > 0 ? seconds : 300
}

const aggregateRealmMetrics = (
	items: RealmMiniAppMetrics[],
): DashboardMetricPoint => {
	const cachedAtEpochSecond =
		items[0]?.cachedAtEpochSecond ?? Math.floor(Date.now() / 1000)
	const totals = items.reduce(
		(acc, item) => ({
			launchDeniedTotal: acc.launchDeniedTotal + item.launchesDenied,
			launchSuccessTotal: acc.launchSuccessTotal + item.launchesSuccess,
			ssoFailedTotal: acc.ssoFailedTotal + item.ssoFailed,
			ssoSuccessTotal: acc.ssoSuccessTotal + item.ssoSuccess,
		}),
		{
			launchDeniedTotal: 0,
			launchSuccessTotal: 0,
			ssoFailedTotal: 0,
			ssoSuccessTotal: 0,
		},
	)

	return {
		...totals,
		timestamp: new Date(cachedAtEpochSecond * 1000).toISOString(),
	}
}

const appendSnapshotPoint = (
	points: DashboardMetricPoint[],
	snapshot: DashboardMetricPoint,
) => {
	const snapshotTime = new Date(snapshot.timestamp).getTime()
	const latest = points.at(-1)

	if (!latest) {
		return [snapshot]
	}

	const latestTime = new Date(latest.timestamp).getTime()

	if (latestTime === snapshotTime) {
		return [...points.slice(0, -1), snapshot]
	}

	if (latestTime > snapshotTime) {
		return points
	}

	return [...points, snapshot].slice(-24)
}

const DashboardSkeleton = () => (
	<>
		<DashboardMetricsGrid loading />
		<div className={styles.chartsGrid}>
			{Array.from({ length: 3 }, (_, index) => (
				<Card className={styles.skeletonCard} key={index}>
					<Skeleton active paragraph={{ rows: 8 }} />
				</Card>
			))}
		</div>
	</>
)

export const DashboardPage = () => {
	const [searchParams] = useSearchParams()
	const user = useUserStore(state => state.user)
	const [chartPoints, setChartPoints] = useState<DashboardMetricPoint[]>([])
	const metricsParams = useMemo(() => {
		const range = searchParams.get('range')?.trim() || '24h'
		const realmCode = searchParams.get('realmCode')?.trim()

		return {
			range,
			realmCode: realmCode || undefined,
		}
	}, [searchParams])

	const canListRealms = user?.role === Roles.ROOT
	const realmsQuery = useQuery({
		enabled: canListRealms && !metricsParams.realmCode,
		queryFn: () => getAdminRealms({ size: 100 }),
		queryKey: realmKeys.list({ size: 100 }),
		staleTime: 30_000,
	})
	const selectedRealmCode =
		metricsParams.realmCode ||
		user?.realmCode ||
		realmsQuery.data?.items[0]?.code
	const windowSeconds = parseWindowSeconds(metricsParams.range)

	const {
		data: metrics = [],
		dataUpdatedAt,
		error,
		isError,
		isLoading,
		isSuccess,
		refetch,
	} = useQuery({
		enabled: Boolean(selectedRealmCode),
		queryFn: () =>
			getRealmMiniAppMetrics({
				realmCode: selectedRealmCode!,
				windowSeconds,
			}),
		queryKey: dashboardKeys.realmMiniApps({
			realmCode: selectedRealmCode,
			windowSeconds,
		}),
		refetchInterval: 10_000,
		placeholderData: previousData => previousData,
	})

	const points = useMemo(() => {
		if (!selectedRealmCode || (!isSuccess && metrics.length === 0)) {
			return []
		}

		return [aggregateRealmMetrics(metrics)]
	}, [isSuccess, metrics, selectedRealmCode])
	useEffect(() => {
		setChartPoints([])
	}, [selectedRealmCode, windowSeconds])

	useEffect(() => {
		if (!selectedRealmCode || !isSuccess) {
			return
		}

		const snapshot = aggregateRealmMetrics(metrics)
		setChartPoints(previousPoints =>
			appendSnapshotPoint(previousPoints, snapshot),
		)
	}, [dataUpdatedAt, isSuccess, metrics, selectedRealmCode, windowSeconds])
	const chartData = useMemo(
		() => buildDashboardChartData(chartPoints),
		[chartPoints],
	)
	const latest = points.at(-1)
	const hasPoints = points.length > 0
	const errorStatus = getErrorStatus(error || realmsQuery.error)
	const isForbidden = errorStatus === 403
	const isInitialLoading =
		((canListRealms && realmsQuery.isLoading) || isLoading) && !hasPoints
	const isFirstLoadError = isError && !hasPoints
	const isPollingError = isError && hasPoints

	const renderContent = () => {
		if (isInitialLoading) {
			return <DashboardSkeleton />
		}

		if (isForbidden && !hasPoints) {
			return (
				<Card className={styles.stateCard}>
					<Result status='403' title='Нет доступа к дашборду' />
				</Card>
			)
		}

		if (isFirstLoadError) {
			return (
				<Card className={styles.stateCard}>
					<Result
						extra={
							<Button onClick={() => void refetch()} type='primary'>
								Повторить
							</Button>
						}
						status='error'
						title='Не удалось загрузить метрики'
					/>
				</Card>
			)
		}

		if (!latest) {
			return (
				<Card className={styles.stateCard}>
					<Empty description='Нет данных для отображения' />
				</Card>
			)
		}

		return (
			<>
				<DashboardMetricsGrid latest={latest} />
				<div className={styles.chartsGrid}>
					<LaunchesDeltaChart
						data={chartData}
						periodLabel={metricsParams.range}
					/>
					<SsoDeltaChart
						data={chartData}
						periodLabel={metricsParams.range}
					/>
					<CallsCountRing
						latest={latest}
						periodLabel={metricsParams.range}
					/>
					<TotalsOverviewChart latest={latest} />
				</div>
			</>
		)
	}

	return (
		<div className='container'>
			<div className={styles.wrapper}>
				<div className={styles.header}>
					<div>
						<Title className={styles.title} level={1}>
							Дашборд
						</Title>
						<Text className={styles.subtitle}>
							Статистика платформы, которая обновляется каждые 10 секунд
						</Text>
					</div>

					<div className={styles.headerMeta}>
						<div className={styles.scope}>
							<div>
								<Text type='secondary'>Realm</Text>
								<Text strong>{selectedRealmCode ?? '—'}</Text>
							</div>
							<div>
								<Text type='secondary'>Окно</Text>
								<Text strong>{metricsParams.range}</Text>
							</div>
						</div>
						<div className={styles.updatedAt}>
							<Text type='secondary'>Обновлено</Text>
							<Text strong>{formatUpdatedAt(dataUpdatedAt)}</Text>
						</div>
					</div>
				</div>

				{isPollingError && (
					<Alert
						message={
							isForbidden
								? 'Нет доступа к дашборду'
								: 'Не удалось обновить данные. Показываются последние доступные значения.'
						}
						showIcon
						type={isForbidden ? 'error' : 'warning'}
					/>
				)}

				{renderContent()}
			</div>
		</div>
	)
}
