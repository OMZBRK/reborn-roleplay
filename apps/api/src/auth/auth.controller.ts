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
import { LoginDto, RefreshDto } from './dto/login.dto';
import { JwtAuthGuard } from './jwt-auth.guard';
import { CurrentUser } from './current-user.decorator';
import type { RequestUser } from './current-user.decorator';

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
    return this.auth.refresh(dto.refreshToken, { userAgent, ip });
  }

  @Post('logout')
  @HttpCode(HttpStatus.NO_CONTENT)
  @UseGuards(JwtAuthGuard)
  async logout(@CurrentUser() user: RequestUser) {
    await this.auth.logout(user.sub);
  }

  @Get('me')
  @UseGuards(JwtAuthGuard)
  async me(@CurrentUser() user: RequestUser) {
    return this.auth.getCurrentUser(user.sub);
  }
}
