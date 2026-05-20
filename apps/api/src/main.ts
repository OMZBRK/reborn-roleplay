import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { json } from 'express';
import { AppModule } from './app.module';

async function bootstrap() {
  // CORS gere par Caddy en prod (cf infra/Caddyfile §API) : il pose
  // Access-Control-Allow-Origin avec la valeur exacte du panel.
  // Si on laisse `cors: true` ici, Nest ajoute "Access-Control-Allow-Origin: *"
  // et le browser voit DEUX valeurs dans le header (`panel, *`) → bloque
  // toutes les requetes cross-origin avec erreur "multiple values".
  //
  // En dev local (sans Caddy) on s'appuie sur Nest pour configurer le CORS
  // avec l'origin du launcher Tauri (`tauri://localhost` / `http://localhost:1420`).
  const isProd = process.env.NODE_ENV === 'production';
  const app = await NestFactory.create(AppModule, {
    cors: isProd
      ? false
      : {
          origin: true,
          credentials: true,
        },
    bodyParser: false,
  });

  // Capture le body brut sur req.rawBody pour les routes /v1/staff/* qui
  // verifient une signature HMAC sur les bytes avant deserialisation
  // JSON. Pour le reste, JSON.parse standard.
  app.use(
    json({
      limit: '1mb',
      verify: (req, _res, buf) => {
        (req as { rawBody?: Buffer }).rawBody = Buffer.from(buf);
      },
    }),
  );

  app.setGlobalPrefix('v1');
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  const port = Number(process.env.PORT ?? 3000);
  await app.listen(port);
  // eslint-disable-next-line no-console
  console.log(`[reborn-api] listening on http://localhost:${port}/v1`);
}

void bootstrap();
