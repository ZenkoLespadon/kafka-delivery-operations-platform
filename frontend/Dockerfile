FROM node:22-alpine AS build
WORKDIR /app

ARG NEXT_PUBLIC_BACKEND_URL=http://localhost:8080
ENV NEXT_PUBLIC_BACKEND_URL=${NEXT_PUBLIC_BACKEND_URL}

COPY package*.json ./
RUN npm ci

COPY . .
RUN mkdir -p public
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000

COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000
CMD ["node", "server.js"]
