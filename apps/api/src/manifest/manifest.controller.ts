import { Controller, Get, UseGuards } from '@nestjs/common';
import { ManifestService } from './manifest.service';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';

@Controller('manifest')
@UseGuards(JwtAuthGuard)
export class ManifestController {
  constructor(private readonly manifest: ManifestService) {}

  @Get('current')
  current() {
    return this.manifest.getCurrent();
  }
}
