import { apiClient, buildQueryParams, unwrapData } from '@/shared/api'

import type { DashboardMetricPoint, RealmMiniAppMetrics } from '../model/types'

export type DashboardMetricsParams = {
	miniAppCode?: string
	range?: string
	realmCode?: string
}

type DashboardMetricsResponse =
	| DashboardMetricPoint[]
	| {
			items: DashboardMetricPoint[]
	  }
	| {
			points: DashboardMetricPoint[]
	  }

export const dashboardKeys = {
	all: ['dashboard'] as const,
	metrics: (params?: DashboardMetricsParams) =>
		[...dashboardKeys.all, 'metrics', params] as const,
	realmMiniApps: (params: {
		miniAppCode?: string
		realmCode?: string
		windowSeconds?: number
	}) => [...dashboardKeys.all, 'realm-miniapps', params] as const,
}

const normalizeDashboardMetrics = (
	response: DashboardMetricsResponse,
): DashboardMetricPoint[] => {
	if (Array.isArray(response)) {
		return response
	}

	if ('points' in response) {
		return response.points
	}

	return response.items
}

export const getDashboardMetrics = async (
	params?: DashboardMetricsParams,
): Promise<DashboardMetricPoint[]> => {
	const data = await unwrapData(
		apiClient.get<DashboardMetricsResponse>(
			'/api/admin/statistics/dashboard',
			{
				params: buildQueryParams(params),
			},
		),
	)

	return normalizeDashboardMetrics(data)
}

export const getRealmMiniAppMetrics = async ({
	miniAppCode,
	realmCode,
	windowSeconds,
}: {
	miniAppCode?: string
	realmCode: string
	windowSeconds?: number
}): Promise<RealmMiniAppMetrics[]> => {
	const endpoint = miniAppCode
		? `/api/admin/realms/${realmCode}/metrics/miniapps/${miniAppCode}`
		: `/api/admin/realms/${realmCode}/metrics/miniapps`
	const data = await unwrapData(
		apiClient.get<RealmMiniAppMetrics[] | RealmMiniAppMetrics>(endpoint, {
			params: buildQueryParams({ windowSeconds }),
		}),
	)

	return Array.isArray(data) ? data : [data]
}
