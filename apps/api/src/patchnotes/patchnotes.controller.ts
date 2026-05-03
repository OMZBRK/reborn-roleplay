import {
  Controller,
  DefaultValuePipe,
  Get,
  Param,
  ParseIntPipe,
  Query,
  UseGuards,
} from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { PatchnotesService } from './patchnotes.service';

@Controller('patchnotes')
@UseGuards(JwtAuthGuard)
export class PatchnotesController {
  constructor(private readonly service: PatchnotesService) {}

  @Get()
  async list(
    @Query('page', new DefaultValuePipe(1), ParseIntPipe) page: number,
    @Query('size', new DefaultValuePipe(10), ParseIntPipe) size: number,
  ) {
    return this.service.list(page, size);
  }

  @Get(':id')
  async detail(@Param('id') id: string) {
    return this.service.getById(id);
  }
}
