import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { PassportModule } from '@nestjs/passport';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { JwtStrategy } from './jwt.strategy';
import { MojangService } from './mojang.service';

@Module({
  imports: [
    PassportModule.register({ defaultStrategy: 'jwt' }),
    JwtModule.registerAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService) => {
        const secret = config.get<string>('JWT_SECRET');
        if (!secret) {
          throw new Error(
            'JWT_SECRET manquant dans .env (cf apps/api/.env.example)',
          );
        }
        return {
          secret,
          signOptions: {
            issuer: config.get('JWT_ISSUER', 'reborn-rp'),
            audience: config.get('JWT_AUDIENCE', 'reborn-launcher'),
            expiresIn: config.get('JWT_ACCESS_TTL', '15m'),
          },
        };
      },
    }),
  ],
  controllers: [AuthController],
  providers: [AuthService, JwtStrategy, MojangService],
  exports: [AuthService, JwtStrategy],
})
export class AuthModule {}
