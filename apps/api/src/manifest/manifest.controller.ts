import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Post,
  UseGuards,
} from '@nestjs/common';
import { Role } from '@prisma/client';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { MinRole } from '../auth/roles.decorator';
import { RolesGuard } from '../auth/roles.guard';
import { PublishManifestDto } from './manifest.dto';
import { ManifestService } from './manifest.service';

@Controller('manifest')
@UseGuards(JwtAuthGuard)
export class ManifestController {
  constructor(private readonly manifest: ManifestService) {}

  @Get('current')
  current() {
    return this.manifest.getCurrent();
  }
}

/** Endpoints admin pour publier / lister les manifests. ADMIN+ only. */
@Controller('admin/manifest')
@UseGuards(JwtAuthGuard, RolesGuard)
@MinRole(Role.ADMIN)
export class AdminManifestController {
  constructor(private readonly manifest: ManifestService) {}

  @Get()
  list() {
    return this.manifest.list();
  }

  @Post()
  @HttpCode(HttpStatus.CREATED)
  publish(@Body() dto: PublishManifestDto, @CurrentUser() user: RequestUser) {
    return this.manifest.publish(dto, user.sub);
  }
}
