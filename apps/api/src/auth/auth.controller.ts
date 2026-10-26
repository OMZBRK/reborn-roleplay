import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Headers,
  Ip,
  Post,
  UseGuards,
} from '@nestjs/common';
import { AuthService } from './auth.service';
import { DevLoginDto, LoginDto, RefreshDto } from './dto/login.dto';
import { JwtAuthGuard } from './jwt-auth.guard';
import { CurrentUser } from './current-user.decorator';
import type { RequestUser } from './current-user.decorator';
import { capacitesDe } from './capacites';

@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  @Post('login')
  @HttpCode(HttpStatus.OK)
  async login(
    @Body() dto: LoginDto,
    @Headers('user-agent') userAgent: string | undefined,
    @Ip() ip: string,
  ) {
    return this.auth.loginWithMinecraft(dto.mcAccessToken, { userAgent, ip });
  }

  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  async refresh(
    @Body() dto: RefreshDto,
    @Headers('user-agent') userAgent: string | undefined,
    @Ip() ip: string,
  ) {
    return this.auth.refresh(dto.refreshToken, { userAgent, ip }, { nouvelleFamille: dto.nouvelleFamille });
  }

  /**
   * Dev-only : login factice tant que MS_CLIENT_ID n'est pas approuve.
   * Cf docs/adr/0001-microsoft-app-approval-required.md.
   */
  @Post('dev-login')
  @HttpCode(HttpStatus.OK)
  async devLogin(
    @Body() dto: DevLoginDto,
    @Headers('user-agent') userAgent: string | undefined,
    @Ip() ip: string,
  ) {
    return this.auth.devLogin(dto.username, { userAgent, ip });
  }

  @Post('logout')
  @HttpCode(HttpStatus.NO_CONTENT)
  @UseGuards(JwtAuthGuard)
  async logout(@CurrentUser() user: RequestUser) {
    await this.auth.logout(user.sub);
  }

  /**
   * Profil du compte connecté (launcher et panel staff). `capacites` : ce que
   * le grade permet au panel (auth/capacites.ts, PANEL_V2 §3.3) ; vide pour un
   * joueur. Le grade est relu en base à chaque requête (JwtStrategy), donc un
   * changement de grade se voit au prochain appel.
   */
  @Get('me')
  @UseGuards(JwtAuthGuard)
  async me(@CurrentUser() user: RequestUser) {
    const profil = await this.auth.getCurrentUser(user.sub);
    return { ...profil, capacites: capacitesDe(profil.role) };
  }
}
