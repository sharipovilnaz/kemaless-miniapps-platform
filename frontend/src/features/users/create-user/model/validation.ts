import { Roles, type PlatformUserStatus, type Role } from '@/entities/user'
import { z } from 'zod'

export const createUserSchema = z
	.object({
		confirmPassword: z.string().min(1, 'Подтвердите пароль'),
		email: z.string().email('Введите корректный email'),
		password: z.string().min(6, 'Минимум 6 символов'),
		realmCode: z.string(),
		role: z.nativeEnum(Roles),
		status: z.enum(['active', 'disable']),
		username: z.string().min(1, 'Это обязательное поле'),
	})
	.refine(values => values.password === values.confirmPassword, {
		message: 'Пароли не совпадают',
		path: ['confirmPassword'],
	})
	.refine(
		values => values.role === Roles.ROOT || values.realmCode.trim().length > 0,
		{
			message: 'Выберите Realm',
			path: ['realmCode'],
		},
	)
	.refine(values => values.role !== Roles.ROOT || !values.realmCode.trim(), {
		message: 'ROOT не должен быть привязан к Realm',
		path: ['realmCode'],
	})

export type CreateUserFormValues = {
	confirmPassword: string
	email: string
	password: string
	realmCode: string
	role: Role
	status: PlatformUserStatus
	username: string
}
