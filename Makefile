DOCKER_COMPOSE = docker compose
MVN = ./mvnw

GREEN=\033[0;32m
YELLOW=\033[0;33m
NC=\033[0m

.PHONY: all build up down restart logs clean clean-data ps help

default: help

all: build up
	@echo "${GREEN}Базовая инфраструктура платформы (core, postgres, minio) запущена.${NC}"
	@echo "${GREEN}Core API: http://localhost:$${SERVER_PORT:-8080}${NC}"

build:
	@echo "${YELLOW}Собираем Java-модули...${NC}"
	cd miniapp-sdk && $(MVN) clean install -DskipTests
	cd core && $(MVN) clean package -DskipTests
	cd payment-miniapp && $(MVN) clean package -DskipTests
	cd kemaless-bank && sh $(MVN) clean package -DskipTests
	@echo "${GREEN}Сборка завершена.${NC}"

up:
	@echo "${YELLOW}Поднимаем инфраструктуру через Docker Compose...${NC}"
	$(DOCKER_COMPOSE) up --build -d
	@echo "${GREEN}Инфраструктура поднята.${NC}"

down:
	@echo "${YELLOW}Останавливаем контейнеры...${NC}"
	$(DOCKER_COMPOSE) down
	@echo "${GREEN}Контейнеры остановлены.${NC}"

restart: down up

clean: down
	@echo "${YELLOW}Очищаем target директории...${NC}"
	cd core && $(MVN) clean
	cd miniapp-sdk && $(MVN) clean
	cd payment-miniapp && $(MVN) clean
	@echo "${GREEN}Очистка завершена.${NC}"

clean-data: down
	@echo "${YELLOW}Удаляем контейнеры и все volumes...${NC}"
	$(DOCKER_COMPOSE) down -v
	@echo "${GREEN}Данные очищены.${NC}"

logs:
	$(DOCKER_COMPOSE) logs -f $(s)

ps:
	$(DOCKER_COMPOSE) ps

help:
	@echo "Доступные команды:"
	@echo "  ${YELLOW}make all${NC}        - Сборка Java-модулей и запуск базового Docker Compose"
	@echo "  ${YELLOW}make build${NC}      - Сборка miniapp-sdk, core, payment-miniapp и kemaless-bank"
	@echo "  ${YELLOW}make up${NC}         - Запуск docker compose up --build -d"
	@echo "  ${YELLOW}make down${NC}       - Остановка контейнеров"
	@echo "  ${YELLOW}make restart${NC}    - Перезапуск контейнеров"
	@echo "  ${YELLOW}make logs${NC}       - Просмотр логов (опционально s=<service>)"
	@echo "  ${YELLOW}make clean${NC}      - Остановка контейнеров и очистка target/"
	@echo "  ${YELLOW}make clean-data${NC} - Остановка контейнеров и удаление volumes"
	@echo "  ${YELLOW}make ps${NC}         - Статус контейнеров"
