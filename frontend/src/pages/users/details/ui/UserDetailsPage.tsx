import {
	platformUserStatusColors,
	platformUserStatusLabels,
	getPlatformUser,
	updatePlatformUserAndAccess,
	userKeys,
	roleLabels,
	Roles,
	type PlatformUserStatus,
	type Role,
} from '@/entities/user'
import { getAdminRealms, realmKeys } from '@/entities/realm'
import { buildRealmOverviewRoute, ROUTES } from '@/shared/config'
import { ButtonField } from '@/shared/ui/ButtonField'
import { InputField } from '@/shared/ui/InputField'
import { SelectField } from '@/shared/ui/SelectField'
import { UserManageActions } from '@/widgets/user-manage-actions'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Card, message, Tag, Typography } from 'antd'
import { useEffect, useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useParams } from 'react-router-dom'
import styles from './UserDetailsPage.module.css'

const { Text, Title } = Typography

type UserAccessForm = {
	email: string
	realmCode: string
	role: Role
	status: PlatformUserStatus
}

const roleOptions = [Roles.ROOT, Roles.ADMIN, Roles.READONLY].map(role => ({
	label: roleLabels[role],
	value: role,
}))

const statusOptions = [
	{ label: platformUserStatusLabels.active, value: 'active' },
	{ label: platformUserStatusLabels.disable, value: 'disable' },
]

const formatDate = (value: string) => new Date(value).toLocaleString()

export const UserDetailsPage = () => {
	const { userId = '' } = useParams()
	const queryClient = useQueryClient()
	const { data: user, isLoading: isUserLoading } = useQuery({
		enabled: Boolean(userId),
		queryFn: () => getPlatformUser(userId),
		queryKey: userKeys.detail(userId),
	})
	const { data: realmsPage } = useQuery({
		queryFn: () => getAdminRealms(),
		queryKey: realmKeys.list(),
	})
	const realms = realmsPage?.items ?? []
	const realmOptions = useMemo(
		() =>
			realms.map(realm => ({
				label: `${realm.name} (${realm.code})`,
				value: realm.code,
			})),
		[realms],
	)

	const {
		control: accessControl,
		formState: { isDirty, isValid },
		handleSubmit: handleAccessSubmit,
		reset,
		setValue,
		watch,
	} = useForm<UserAccessForm>({
		defaultValues: {
			email: user?.email ?? '',
			realmCode: user?.realmCode ?? '',
			role: user?.role ?? Roles.ADMIN,
			status: user?.status ?? 'active',
		},
		mode: 'onBlur',
	})
	const selectedRole = watch('role')
	const selectedRealmCode = watch('realmCode')
	const isRootRole = selectedRole === Roles.ROOT
	const updateUserMutation = useMutation({
		mutationFn: (values: UserAccessForm) =>
			user
				? updatePlatformUserAndAccess(user, values)
				: Promise.reject(new Error('User is required')),
		onSuccess: updatedUser => {
			void queryClient.invalidateQueries({ queryKey: userKeys.lists() })
			void queryClient.invalidateQueries({
				queryKey: userKeys.detail(updatedUser.id),
			})
			message.success('Пользователь обновлён')
		},
		onError: () => {
			message.error('Не удалось обновить пользователя')
		},
	})

	useEffect(() => {
		if (user) {
			reset({
				email: user.email,
				realmCode: user.realmCode ?? '',
				role: user.role,
				status: user.status,
			})
		}
	}, [reset, user])

	useEffect(() => {
		if (isRootRole) {
			if (selectedRealmCode) {
				setValue('realmCode', '', {
					shouldDirty: true,
					shouldValidate: true,
				})
			}
			return
		}

		if (!selectedRealmCode && realmOptions[0]?.value) {
			setValue('realmCode', realmOptions[0].value, {
				shouldDirty: true,
				shouldValidate: true,
			})
		}
	}, [isRootRole, realmOptions, selectedRealmCode, setValue])

	if (isUserLoading) {
		return null
	}

	if (!user) {
		return <Navigate to={ROUTES.NOT_FOUND} />
	}

	const userRealm = realms.find(realm => realm.code === user.realmCode)

	const handleUpdateAccess = (values: UserAccessForm) => {
		if (values.role !== Roles.ROOT && !values.realmCode) {
			message.error('Выберите Realm')
			return
		}

		updateUserMutation.mutate(values)
	}

	const handleReset = () => {
		reset({
			email: user.email,
			realmCode: user.realmCode ?? '',
			role: user.role,
			status: user.status,
		})
	}

	return (
		<div className={`container ${styles.wrapper}`}>
			<div className={styles.header}>
				<Text type='secondary'>Страница пользователя host-app</Text>
				<Title className={styles.mainTitle} level={1}>
					{user.username}
				</Title>
			</div>

			<div className={styles.grid}>
				<Card className={styles.card}>
					<Title className={styles.sectionTitle} level={3}>
						Профиль
					</Title>
					<div className={styles.rows}>
						<div className={styles.row}>
							<Text className={styles.label}>Username</Text>
							<Text className={styles.value}>{user.username}</Text>
						</div>
						<div className={styles.row}>
							<Text className={styles.label}>Email</Text>
							<Text className={styles.value}>{user.email}</Text>
						</div>
						<div className={styles.row}>
							<Text className={styles.label}>Realm</Text>
							<Text className={styles.value}>
								{user.realmCode ? (
									<Link
										className={styles.realmLink}
										to={buildRealmOverviewRoute(user.realmCode)}
									>
										{userRealm
											? `${userRealm.name} (${userRealm.code})`
											: user.realmCode}
									</Link>
								) : (
									'Без Realm'
								)}
							</Text>
						</div>
						<div className={styles.row}>
							<Text className={styles.label}>Статус</Text>
							<div>
								<Tag color={platformUserStatusColors[user.status]}>
									{platformUserStatusLabels[user.status]}
								</Tag>
							</div>
						</div>
						<div className={styles.row}>
							<Text className={styles.label}>Роль</Text>
							<div>
								<Tag color='geekblue'>{roleLabels[user.role]}</Tag>
							</div>
						</div>
						<div className={styles.row}>
							<Text className={styles.label}>Дата создания</Text>
							<Text className={styles.value}>{formatDate(user.createdAt)}</Text>
						</div>
						<div className={styles.row}>
							<Text className={styles.label}>Последнее обновление</Text>
							<Text className={styles.value}>{formatDate(user.updatedAt)}</Text>
						</div>
					</div>
				</Card>

				<Card className={styles.card}>
					<Title className={styles.sectionTitle} level={3}>
						Доступ
					</Title>
					<form
						className={styles.form}
						onSubmit={handleAccessSubmit(handleUpdateAccess)}
					>
						<InputField
							control={accessControl}
							label='Email'
							name='email'
							placeholder='user@example.com'
						/>

						<SelectField<UserAccessForm, Role>
							control={accessControl}
							label='Роль'
							name='role'
							options={roleOptions}
							placeholder='Выберите роль'
						/>

						<SelectField<UserAccessForm, PlatformUserStatus>
							control={accessControl}
							label='Статус'
							name='status'
							options={statusOptions}
							placeholder='Выберите статус'
						/>

						<SelectField<UserAccessForm, string>
							control={accessControl}
							disabled={isRootRole}
							label='Realm'
							name='realmCode'
							options={realmOptions}
							placeholder='Выберите Realm'
						/>
						<div className={styles.actions}>
							<UserManageActions
								showView={false}
								user={user}
								variant='inline'
								className={styles.deleteAction}
							/>

							<ButtonField onClick={handleReset}>Очистить</ButtonField>
							<ButtonField
								disabled={!isDirty || !isValid}
								htmlType='submit'
								loading={updateUserMutation.isPending}
								type='primary'
							>
								Сохранить
							</ButtonField>
						</div>
					</form>
				</Card>
			</div>
		</div>
	)
}
