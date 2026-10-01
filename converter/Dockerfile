FROM python:3.12-slim-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends \
    libreoffice-writer libreoffice-calc fonts-dejavu-core fonts-liberation \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10001 --create-home converter
RUN apt-get update && apt-get install -y --no-install-recommends python3-uno \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /service
COPY server.py convert_document.py ./
USER 10001:10001
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
EXPOSE 8090
CMD ["python", "server.py"]
