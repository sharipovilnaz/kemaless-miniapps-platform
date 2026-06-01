import type {
	DashboardMetricChartPoint,
	DashboardMetricPoint,
} from '../model/types'

export const buildDashboardChartData = (
	points: DashboardMetricPoint[],
): DashboardMetricChartPoint[] =>
	points.map(point => ({
		...point,
		timeLabel: new Date(point.timestamp).toLocaleTimeString('ru-RU', {
			hour: '2-digit',
			minute: '2-digit',
			second: '2-digit',
		}),
		launchSuccessDelta: point.launchSuccessTotal,
		launchDeniedDelta: point.launchDeniedTotal,
		ssoSuccessDelta: point.ssoSuccessTotal,
		ssoFailedDelta: point.ssoFailedTotal,
	}))
