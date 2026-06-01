DOCKER_COMPOSE = docker compose
MVN = ./mvnw

GREEN=\033[0;32m
YELLOW=\033[0;33m
NC=\033[0m

.PHONY: all build up down restart logs clean clean-data ps help

default: help

all: build up
	@echo "${GREEN}✅ Платформа успешно развернута и запущена!${NC}"
	@echo "${GREEN}📊 Grafana: http://localhost:3323 (admin/admin)${NC}"

build:
	@echo "${YELLOW}⏳ Собираем Java приложения...${NC}"
	cd core && $(MVN) clean package -DskipTests
	cd payment-miniapp && $(MVN) clean package -DskipTests
	cd kemaless-bank && $(MVN) clean package -DskipTests
	@echo "${GREEN}✅ Сборка завершена.${NC}"

up:
	@echo "${YELLOW}🐳 Поднимаем инфраструктуру через Docker Compose...${NC}"
	$(DOCKER_COMPOSE) up --build -d
	@echo "${GREEN}✅ Инфраструктура поднята.${NC}"

down:
	@echo "${YELLOW}🛑 Останавливаем контейнеры...${NC}"
	$(DOCKER_COMPOSE) down
	@echo "${GREEN}✅ Контейнеры остановлены.${NC}"

restart: down up

clean: down
	@echo "${YELLOW}🧹 Очищаем target директории...${NC}"
	$(MVN) clean
	@echo "${GREEN}✅ Очистка завершена.${NC}"

clean-data: down
	@echo "${YELLOW}⚠️ Удаляем контейнеры и ВСЕ данные (volumes)...${NC}"
	$(DOCKER_COMPOSE) down -v
	@echo "${GREEN}✅ Данные очищены.${NC}"

logs:
	$(DOCKER_COMPOSE) logs -f $(s)

ps:
	$(DOCKER_COMPOSE) ps

help:
	@echo "Доступные команды:"
	@echo "  ${YELLOW}make all${NC}        - 🚀 Полная сборка Java кода и запуск всех контейнеров (Single-Click)"
	@echo "  ${YELLOW}make build${NC}      - 🔨 Только компиляция Java кода (mvn clean package)"
	@echo "  ${YELLOW}make up${NC}         - 🐳 Только запуск docker-compose up -d"
	@echo "  ${YELLOW}make down${NC}       - 🛑 Остановка всех сервисов"
	@echo "  ${YELLOW}make restart${NC}    - 🔄 Перезапуск платформы"
	@echo "  ${YELLOW}make logs${NC}       - 📜 Просмотр логов всех контейнеров (остановить: Ctrl+C)"
	@echo "  ${YELLOW}make clean${NC}      - 🧹 Остановка контейнеров и удаление папок target/"
	@echo "  ${YELLOW}make clean-data${NC} - ⚠️ Полное удаление контейнеров вместе с базами данных (volumes)"
	@echo "  ${YELLOW}make ps${NC}         - 👀 Посмотреть статус запущенных контейнеров"